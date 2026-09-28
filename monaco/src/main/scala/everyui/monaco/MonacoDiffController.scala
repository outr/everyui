package everyui.monaco

import fabric.*
import fabric.rw.*
import reactify.*

import scala.concurrent.{Future, Promise}

/** A diff editor, driven from the app: the original text (left, or above) against the modified (right, or below), in
  * one language. Made before its editor, as MonacoController is, and handed to a MonacoDiffEditor; what is set before
  * it is made is what it is made with. */
class MonacoDiffController(originalValue: String = "", modifiedValue: String = "", initialLanguage: String = "plaintext",
                           initialTheme: String = "vs-dark", initialOptions: MonacoDiffOptions = MonacoDiffOptions(),
                           editorOptions: MonacoEditorOptions = MonacoEditorOptions()) {
  import Monaco.uiThread

  private val left = Var(originalValue)
  private val right = Var(modifiedValue)
  private val lang = Var(initialLanguage)
  private val themed = Var(initialTheme)
  private val opts = Var(initialOptions)
  private val live = Var(false)

  val original: Val[String] = left
  val modified: Val[String] = right
  val language: Val[String] = lang
  val theme: Val[String] = themed
  val options: Val[MonacoDiffOptions] = opts
  val attached: Val[Boolean] = live

  /** The user changed a side (not the app, through `setOriginalValue` or `setModifiedValue`): its new text. */
  val onOriginalChange: Channel[String] = Channel[String]
  val onModifiedChange: Channel[String] = Channel[String]
  private val regions = Var[List[MonacoLineChange]](Nil)
  /** The changed regions, as Monaco last worked them out. */
  val changes: Val[List[MonacoLineChange]] = regions
  /** The differences were worked out again (after an edit to either side): the changed regions. */
  val onDidUpdateDiff: Channel[List[MonacoLineChange]] = Channel[List[MonacoLineChange]]
  /** The checkboxes in the modified side's gutter (MonacoDiffCheck), as the app sets them: none by default. */
  val checks: Var[List[MonacoDiffCheck]] = Var(Nil)
  /** A checkbox in the gutter was clicked: the line, which kind, and the change there. */
  val onGutterClick: Channel[MonacoDiffClick] = Channel[MonacoDiffClick]
  val onHandBack: Channel[(String, Boolean)] = Channel[(String, Boolean)]

  private var bridge: Option[MonacoBridge] = None
  private var diffId = ""
  private var leftMirror = new TextMirror(originalValue, 0)
  private var rightMirror = new TextMirror(modifiedValue, 0)
  private var stopListening: () => Unit = () => ()
  private var attachedOnce = Promise[Unit]()
  private var disposed = false

  def ready: Future[Unit] = attachedOnce.future
  def isAttached: Boolean = bridge.nonEmpty
  def isDisposed: Boolean = disposed

  def setOriginalValue(value: String, keepUndo: Boolean = true): Future[Unit] = {
    check()
    left @= value
    if (!isAttached) Future.unit else invoke("diff.setOriginal", obj("value" -> str(value), "keepUndo" -> bool(keepUndo))).map(_ => ())
  }

  def setModifiedValue(value: String, keepUndo: Boolean = true): Future[Unit] = {
    check()
    right @= value
    if (!isAttached) Future.unit else invoke("diff.setModified", obj("value" -> str(value), "keepUndo" -> bool(keepUndo))).map(_ => ())
  }

  def setLanguage(language: String): Future[Unit] = {
    check()
    lang @= language
    if (!isAttached) Future.unit else invoke("diff.setLanguage", obj("language" -> str(language))).map(_ => ())
  }

  /** `theme` shown, in every editor there is (MonacoThemes). */
  def setTheme(theme: String): Future[Unit] = {
    check()
    themed @= theme
    MonacoThemes.setTheme(theme)
  }

  /** The diff options set in `partial` changed (side by side or inline, whitespace, ...). */
  def updateOptions(partial: MonacoDiffOptions): Future[Unit] = {
    check()
    opts @= partial
    if (!isAttached) Future.unit else invoke("diff.updateOptions", obj("options" -> partial.toJson)).map(_ => ())
  }

  def setRenderSideBySide(sideBySide: Boolean): Future[Unit] = updateOptions(opts().copy(renderSideBySide = Some(sideBySide),
    useInlineViewWhenSpaceIsLimited = Some(!sideBySide)))

  def getOriginalValue(): Future[String] = whenReady("diff.getOriginal").map(j => if (j.isStr) j.asString else left())
  def getModifiedValue(): Future[String] = whenReady("diff.getModified").map(j => if (j.isStr) j.asString else right())
  /** The modified side scrolled to `line`, in the middle. */
  def revealLine(line: Int): Future[Unit] = whenReady("diff.revealLine", obj("line" -> num(line))).map(_ => ())
  /** To the next difference, or the previous. */
  def goToDiff(next: Boolean = true): Future[Unit] =
    whenReady("diff.goToDiff", obj("target" -> str(if (next) "next" else "previous"))).map(_ => ())
  /** The changed regions as Monaco has them now, with their character changes. */
  def lineChanges(): Future[List[MonacoLineChange]] = whenReady("diff.lineChanges").map(MonacoDiffController.changes)

  /** The change holding `line` of the modified side (a deletion, the line it sits on), as last worked out. */
  def changeAt(line: Int): Option[MonacoLineChange] = regions().find(_.holds(line))

  /** `change` rolled back: the original's lines put in place of the modified's, as one edit that can be undone. */
  def revertChange(change: MonacoLineChange): Future[Unit] =
    whenReady("diff.applyChange", obj("from" -> str("original"), "change" -> change.json)).map(_ => ())

  /** `change` taken into the original: the modified's lines put in place of the original's, as one edit that can be
    * undone. */
  def acceptChange(change: MonacoLineChange): Future[Unit] =
    whenReady("diff.applyChange", obj("from" -> str("modified"), "change" -> change.json)).map(_ => ())
  def focus(): Future[Unit] = whenReady("diff.focus").map(_ => ())
  def blur(): Future[Unit] = if (!isAttached) Future.unit else invoke("diff.blur", obj()).map(_ => ())

  def dispose(): Unit = if (!disposed) {
    val was = bridge
    val id = diffId
    detach()
    was.foreach(_.call("diff.dispose", obj("diffId" -> str(id))))
    disposed = true
    attachedOnce.tryFailure(new MonacoBridgeException("the controller was disposed"))
  }

  private[monaco] def createArgs: Json = obj(
    "original" -> str(left()), "modified" -> str(right()), "language" -> str(lang()),
    "options" -> Wire.merge(Wire.merge(editorOptions.toJson, opts().toJson), obj(
      "theme" -> str(MonacoThemes.current.getOrElse(themed())),
      // Monaco goes inline by itself below 900 pixels: side by side asked for is side by side
      "useInlineViewWhenSpaceIsLimited" -> bool(opts().useInlineViewWhenSpaceIsLimited.getOrElse(!opts().renderSideBySide.getOrElse(true))))))

  private[monaco] def attach(to: MonacoBridge, id: String): Unit = {
    bridge = Some(to)
    diffId = id
    leftMirror = new TextMirror(left(), 1)
    rightMirror = new TextMirror(right(), 1)
    stopListening = to.listen(event => if (event.diffId == diffId) heard(event))
    if (checks().nonEmpty) sendChecks()
    live @= true
    attachedOnce.trySuccess(())
  }

  private[monaco] def detach(): Unit = if (isAttached) {
    stopListening()
    bridge = None
    diffId = ""
    live @= false
    if (attachedOnce.isCompleted) attachedOnce = Promise[Unit]()
  }

  private def heard(event: BridgeEvent): Unit = event.`type` match {
    case "diff.original.contentChange" =>
      val change = MonacoContentChange.fromJson(event.payload)
      left @= leftMirror(change)
      if (!change.external) onOriginalChange @= left()
    case "diff.modified.contentChange" =>
      val change = MonacoContentChange.fromJson(event.payload)
      right @= rightMirror(change)
      if (!change.external) onModifiedChange @= right()
    case "diff.updated" =>
      regions @= MonacoDiffController.changes(event.payload.get("changes").getOrElse(arr()))
      onDidUpdateDiff @= regions()
    case "diff.gutterClick" =>
      val line = Wire.int(event.payload, "line")
      val isLine = Wire.string(event.payload, "kind") == "line"
      // a change's box sits on its anchor, which is how a deletion is found
      val at = if (isLine) changeAt(line) else regions().find(_.anchorLine == line).orElse(changeAt(line))
      onGutterClick @= MonacoDiffClick(line, isLine, at)
    case "diff.original.handBack" | "diff.modified.handBack" =>
      onHandBack @= (Wire.string(event.payload, "key") -> Wire.bool(event.payload, "shift"))
    case _ => ()
  }

  private def sendChecks(): Unit = invoke("diff.setChecks", obj("checks" -> arr(checks().map(_.toJson)*)))
  checks.attach(_ => if (isAttached) sendChecks())

  private def invoke(method: String, args: Json): Future[Json] = bridge match {
    case Some(b) => b.call(method, Wire.merge(args, obj("diffId" -> str(diffId))))
    case None => Future.failed(new MonacoBridgeException("no diff editor is showing this controller", method))
  }

  private def whenReady(method: String, args: Json = obj()): Future[Json] = {
    check()
    if (isAttached) invoke(method, args) else ready.flatMap(_ => invoke(method, args))
  }

  private def check(): Unit = if (disposed) throw new IllegalStateException("MonacoDiffController is disposed")
}

object MonacoDiffController {
  private[monaco] def changes(json: Json): List[MonacoLineChange] =
    if (json.isArr) json.asVector.toList.map(_.as[MonacoLineChange]) else Nil

  /** A controller, named as flutter_monaco_editor's is made. */
  def apply(originalValue: String = "", modifiedValue: String = "", language: String = "plaintext", theme: String = "vs-dark",
            renderSideBySide: Boolean = true, readOnly: Boolean = false, ignoreTrimWhitespace: Boolean = false): MonacoDiffController =
    new MonacoDiffController(originalValue, modifiedValue, language, theme, MonacoDiffOptions(renderSideBySide = Some(renderSideBySide),
      readOnly = Some(readOnly), ignoreTrimWhitespace = Some(ignoreTrimWhitespace)))
}
