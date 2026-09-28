package everyui.monaco

import fabric.*
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
  /** The differences were worked out again: how many changed regions there are. */
  val onDidUpdateDiff: Channel[Int] = Channel[Int]
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
  /** The changed regions: (original start, original end, modified start, modified end) lines. */
  def lineChanges(): Future[List[(Int, Int, Int, Int)]] = whenReady("diff.lineChanges").map { j =>
    if (!j.isArr) Nil else j.asVector.toList.map(c => (Wire.int(c, "originalStartLine"), Wire.int(c, "originalEndLine"),
      Wire.int(c, "modifiedStartLine"), Wire.int(c, "modifiedEndLine")))
  }
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
    case "diff.updated" => onDidUpdateDiff @= Wire.int(event.payload, "changes")
    case "diff.original.handBack" | "diff.modified.handBack" =>
      onHandBack @= (Wire.string(event.payload, "key") -> Wire.bool(event.payload, "shift"))
    case _ => ()
  }

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
  /** A controller, named as flutter_monaco_editor's is made. */
  def apply(originalValue: String = "", modifiedValue: String = "", language: String = "plaintext", theme: String = "vs-dark",
            renderSideBySide: Boolean = true, readOnly: Boolean = false, ignoreTrimWhitespace: Boolean = false): MonacoDiffController =
    new MonacoDiffController(originalValue, modifiedValue, language, theme, MonacoDiffOptions(renderSideBySide = Some(renderSideBySide),
      readOnly = Some(readOnly), ignoreTrimWhitespace = Some(ignoreTrimWhitespace)))
}
