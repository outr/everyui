package everyui.monaco

import fabric.*
import reactify.*

import scala.concurrent.Future

/** A document Monaco edits (an open file), opened through `MonacoModels` and shared by every editor that shows it
  * (`MonacoController.switchToModel(uri)`, `MonacoController.forModel`): one text, one undo history, one dirty state,
  * while each editor keeps its own cursor, selection, scroll and folding.
  *
  * Its text is kept here edit by edit, whichever editor made the edit, or none (`setValue`). On the web every editor
  * shows the one model in the page's Monaco. On the desktop and Android each editor is a web view with a Monaco of its
  * own, so each holds a copy of the model, and an edit in one is made in the others too: the text and whether it is
  * dirty are shared there as well, but each copy has its own undo history (one that holds every edit, whoever made
  * it). */
class MonacoModel private[monaco] (val uri: String, initialValue: String, initialLanguage: String) {
  import Monaco.uiThread

  private val text = Var(initialValue)
  private val lang = Var(initialLanguage)
  private val savedText = Var(initialValue)
  private var mirror = new TextMirror(initialValue, 0)
  private var markers = Map.empty[String, List[MonacoMarker]]
  private var disposed = false
  // its copy in each Monaco that has one, made or being made
  private[monaco] var copies: Map[MonacoBridge, Future[Unit]] = Map.empty
  // whether the app holds it (MonacoModels.open, until close)
  private[monaco] var held: Boolean = true

  /** The text, as the editors last had it. */
  val value: Val[String] = text
  val language: Val[String] = lang
  /** The text as it was last saved (`markSaved`): what it was opened with until then. */
  val saved: Val[String] = savedText
  /** Whether the text differs from what was saved: undone back to it, it is clean again. */
  val dirty: Val[Boolean] = Val(text() != savedText())

  /** The user changed the text, in any editor showing it (not the app, through `setValue`): the new text. */
  val onDidChangeContent: Channel[String] = Channel[String]
  /** Any change to the text, the app's own included, as the edits themselves. */
  val onDidChangeModelContent: Channel[MonacoContentChange] = Channel[MonacoContentChange]

  def isDisposed: Boolean = disposed

  /** The text as it is now counted as saved: `dirty` is false until it changes again. */
  def markSaved(): Unit = savedText @= text()

  /** The text replaced in every editor showing it, by default as one edit they can undo. Not heard as the user's
    * change (`onDidChangeContent`). */
  def setValue(value: String, keepUndo: Boolean = true): Future[Unit] = {
    check()
    if (copies.isEmpty) {
      replaced(value)
      Future.unit
    }
    else {
      // in one copy, which tells the others as it would an edit of the user's
      val (bridge, made) = copies.head
      made.flatMap(_ => bridge.call("models.setValue", obj("uri" -> str(uri), "value" -> str(value), "keepUndo" -> bool(keepUndo))))
        .map(_ => ())
    }
  }

  def setLanguage(language: String): Future[Unit] = {
    check()
    lang @= language
    everywhere("models.setLanguage", obj("language" -> str(language)))
  }

  /** The markers of `owner` on this model, in every editor showing it now or later. */
  def setMarkers(markers: List[MonacoMarker], owner: String = "default"): Future[Unit] = {
    check()
    this.markers = if (markers.isEmpty) this.markers - owner else this.markers + (owner -> markers)
    everywhere("markers.set", marking(owner, markers))
  }

  def clearMarkers(owner: String = "default"): Future[Unit] = setMarkers(Nil, owner)

  private def marking(owner: String, list: List[MonacoMarker]): Json =
    obj("owner" -> str(owner), "markers" -> arr(list.map(_.toJson)*))

  /** `method` on every copy. */
  private def everywhere(method: String, args: Json): Future[Unit] =
    Future.sequence(copies.toList.map { case (bridge, made) =>
      made.flatMap(_ => bridge.call(method, Wire.merge(args, obj("uri" -> str(uri)))))
    }).map(_ => ())

  /** Its copy in `bridge`'s Monaco, made from the text as it is here if there is none yet. */
  private[monaco] def in(bridge: MonacoBridge): Future[Unit] = copies.getOrElse(bridge, {
    check()
    MonacoModels.listen(bridge)
    val made = bridge.call("models.open", obj("uri" -> str(uri), "value" -> str(mirror.value), "language" -> str(lang())))
      .map { j =>
        // the first copy says how Monaco holds the text (its line endings made one), and every later copy is made
        // from that: those agree with it
        val now = Wire.string(j, "value")
        if (j.get("value").exists(_.isStr) && now != mirror.value) {
          if (savedText() == mirror.value) savedText @= now
          replaced(now)
        }
        markers.foreach { case (owner, list) => bridge.call("markers.set", Wire.merge(marking(owner, list), obj("uri" -> str(uri)))) }
      }
    copies = copies + (bridge -> made)
    made.failed.foreach(_ => copies = copies - bridge)
    made
  })

  /** What a copy said changed (`model.contentChange`): kept here, and made in the other copies, unless it was made
    * there from another copy already. */
  private[monaco] def changed(from: MonacoBridge, payload: Json): Unit = if (!disposed && !Wire.bool(payload, "relayed")) {
    val change = MonacoContentChange.fromJson(payload)
    val length = mirror.value.length
    text @= mirror(change.copy(versionId = mirror.versionId + 1))
    onDidChangeModelContent @= change
    if (!change.external) onDidChangeContent @= text()
    copies.keys.filterNot(_ eq from).foreach { other =>
      val whole = obj("uri" -> str(uri), "value" -> str(text()), "relayed" -> bool(true))
      if (change.value.nonEmpty) other.call("models.setValue", whole)
      else other.call("models.applyEdits", obj("uri" -> str(uri), "length" -> num(length), "external" -> bool(change.external),
        "changes" -> arr(change.changes.map(c => obj("rangeOffset" -> num(c.rangeOffset), "rangeLength" -> num(c.rangeLength),
          "text" -> str(c.text)))*))).foreach { j =>
        // a copy out of step is given the whole text
        if (!Wire.bool(j, "applied")) other.call("models.setValue", Wire.merge(whole, obj("value" -> str(text()), "keepUndo" -> bool(true))))
      }
    }
  }

  private def replaced(value: String): Unit = {
    mirror.reset(value, mirror.versionId + 1)
    text @= value
  }

  /** Let go of everywhere: every copy closed. */
  private[monaco] def dispose(): Unit = if (!disposed) {
    disposed = true
    copies.keys.foreach(b => if (!b.isClosed) b.call("models.close", obj("uri" -> str(uri))))
    copies = Map.empty
  }

  private[monaco] def forget(bridge: MonacoBridge): Unit = copies = copies - bridge

  private def check(): Unit = if (disposed) throw new IllegalStateException("the model " + uri + " is closed")

  override def toString: String = "MonacoModel(" + uri + ")"
}
