package everyui.monaco

import fabric.*
import fabric.rw.*
import reactify.*

import scala.concurrent.{Future, Promise}

/** One editor, driven from the app: its text, language and options, where its cursor is, what it shows, and what
  * happens in it.
  *
  * Made before its editor exists, and handed to a MonacoEditor. Until the editor has been made (`ready`), what is set
  * here (`setValue`, `setLanguage`, `setReadOnly`, `updateOptions`) is what it is made with, and every other call waits
  * for it. The editor taken off the screen detaches the controller, which can be handed to another; the actions and
  * commands given it go along. `dispose` when done.
  *
  * The state reads as reactify values (`value()`, `position()`), and what happens arrives on channels
  * (`onDidChangeContent.attach(...)`). The text is kept here edit by edit, so reading it costs nothing. */
class MonacoController(initialValue: String = "", initialLanguage: String = "plaintext", initialTheme: String = "vs-dark",
                       initialReadOnly: Boolean = false, initialOptions: MonacoEditorOptions = MonacoEditorOptions()) {
  import Monaco.ui

  private val text = Var(initialValue)
  private val lang = Var(initialLanguage)
  private val themed = Var(initialTheme)
  private val locked = Var(initialReadOnly)
  private val opts = Var(initialOptions)
  private val cursor = Var[Option[MonacoPosition]](None)
  private val selected = Var[Option[MonacoSelection]](None)
  private val live = Var(false)
  private val model = Var("")

  /** The text, as the editor last had it. */
  val value: Val[String] = text
  val language: Val[String] = lang
  /** The theme it asked for last (themes are global: MonacoThemes). */
  val theme: Val[String] = themed
  val readOnly: Val[Boolean] = locked
  val options: Val[MonacoEditorOptions] = opts
  /** Where the cursor is, once the editor has said. */
  val position: Val[Option[MonacoPosition]] = cursor
  val selection: Val[Option[MonacoSelection]] = selected
  /** Whether an editor is showing this. */
  val attached: Val[Boolean] = live
  /** The uri of the model the editor shows. */
  val uri: Val[String] = model
  private val savedText = Var(initialValue)
  // what was saved of each model shown before, so switching back to a file keeps whether it is dirty
  private val savedBy = scala.collection.mutable.Map.empty[String, String]
  /** The text as it was last saved (`markSaved`): the text it was made with until then. */
  val saved: Val[String] = savedText
  /** Whether the text differs from what was saved: undone back to it, it is clean again. */
  val dirty: Val[Boolean] = Val(text() != savedText())

  /** The user changed the text (not the app, through `setValue`): the new text. */
  val onDidChangeContent: Channel[String] = Channel[String]
  /** Any change to the text, the app's own included, as the edits themselves. */
  val onDidChangeModelContent: Channel[MonacoContentChange] = Channel[MonacoContentChange]
  val onDidChangeCursorPosition: Channel[MonacoPosition] = Channel[MonacoPosition]
  val onDidChangeCursorSelection: Channel[MonacoSelection] = Channel[MonacoSelection]
  val onDidScroll: Channel[MonacoScrollEvent] = Channel[MonacoScrollEvent]
  val onDidFocus: Channel[Unit] = Channel[Unit]
  val onDidBlur: Channel[Unit] = Channel[Unit]
  val onKeyDown: Channel[MonacoKeyEvent] = Channel[MonacoKeyEvent]
  val onKeyUp: Channel[MonacoKeyEvent] = Channel[MonacoKeyEvent]
  /** A key the editor had and gave back (Escape with nothing to close, Tab where nothing can be typed), and whether
    * Shift was held: what MonacoEditor hands on to the app's focus. */
  val onHandBack: Channel[(String, Boolean)] = Channel[(String, Boolean)]

  private var bridge: Option[MonacoBridge] = None
  private var editorId: String = ""
  private var mirror = new TextMirror(initialValue, 0)
  private var stopListening: () => Unit = () => ()
  private var attachedOnce = Promise[Unit]()
  private var disposed = false
  private val actions = scala.collection.mutable.LinkedHashMap.empty[String, MonacoAction]
  private val commands = scala.collection.mutable.LinkedHashMap.empty[String, (Int, Option[String], () => Unit)]
  private var commandsMade = 0

  /** Completes once an editor is showing this; after a detach, once one is again. */
  def ready: Future[Unit] = attachedOnce.future
  def isAttached: Boolean = bridge.nonEmpty
  def isDisposed: Boolean = disposed

  // ---- the text, the language, the look ----

  /** The text replaced. By default as one edit the user can undo, the cursor kept where it can be; `keepUndo = false`
    * starts the editor's history again. Not heard as the user's change (`onDidChangeContent`). */
  def setValue(value: String, keepUndo: Boolean = true): Future[Unit] = {
    check()
    text @= value
    if (!isAttached) Future.unit
    else invoke("editor.setValue", obj("value" -> str(value), "keepUndo" -> bool(keepUndo))).map(_ => ())
  }

  /** The text as the editor has it this moment. */
  def getValue(): Future[String] = whenReady("editor.getValue").map(j => if (j.isStr) j.asString else text())

  def setLanguage(language: String): Future[Unit] = {
    check()
    lang @= language
    if (!isAttached) Future.unit else invoke("editor.setLanguage", obj("language" -> str(language))).map(_ => ())
  }

  /** `theme` shown, in every editor there is (themes are global to a page: MonacoThemes). */
  def setTheme(theme: String): Future[Unit] = {
    check()
    themed @= theme
    MonacoThemes.setTheme(theme)
  }

  def setReadOnly(readOnly: Boolean): Future[Unit] = {
    check()
    locked @= readOnly
    if (!isAttached) Future.unit else invoke("editor.updateOptions", obj("options" -> obj("readOnly" -> bool(readOnly)))).map(_ => ())
  }

  /** The options set in `partial` changed; the rest stay as they are. */
  def updateOptions(partial: MonacoEditorOptions): Future[Unit] = {
    check()
    opts @= opts().mergedWith(partial)
    partial.readOnly.foreach(r => locked @= r)
    if (!isAttached) Future.unit else invoke("editor.updateOptions", obj("options" -> partial.toJson)).map(_ => ())
  }

  // ---- the cursor, the selection, what shows ----

  def setPosition(position: MonacoPosition): Future[Unit] =
    whenReady("editor.setPosition", position.json).map(_ => ())
  def getPosition(): Future[Option[MonacoPosition]] =
    whenReady("editor.getPosition").map(j => if (j.isObj) Some(j.as[MonacoPosition]) else None)
  def setSelection(selection: MonacoSelection): Future[Unit] =
    whenReady("editor.setSelection", selection.json).map(_ => ())
  def getSelection(): Future[Option[MonacoSelection]] =
    whenReady("editor.getSelection").map(j => if (j.isObj) Some(j.as[MonacoSelection]) else None)
  /** Every cursor's selection, the first the primary one. */
  def getSelections(): Future[List[MonacoSelection]] =
    whenReady("editor.getSelections").map(j => if (j.isArr) j.asVector.toList.map(_.as[MonacoSelection]) else Nil)
  /** The editor's own focus: the caret in its text. */
  def focus(): Future[Unit] = whenReady("editor.focus").map(_ => ())
  /** The caret taken out of the text. */
  def blur(): Future[Unit] = if (!isAttached) Future.unit else invoke("editor.blur", obj()).map(_ => ())
  def revealLine(line: Int): Future[Unit] = whenReady("editor.revealLine", obj("line" -> num(line))).map(_ => ())
  def revealLineInCenter(line: Int): Future[Unit] =
    whenReady("editor.revealLineInCenter", obj("line" -> num(line))).map(_ => ())
  def revealRange(range: MonacoRange): Future[Unit] = whenReady("editor.revealRange", range.json).map(_ => ())
  def revealRangeInCenter(range: MonacoRange): Future[Unit] =
    whenReady("editor.revealRangeInCenter", range.json).map(_ => ())

  /** Edits made as the user's would be (undoable), each `range` replaced by its text. */
  def executeEdits(edits: List[(MonacoRange, String)], source: String = "everyui"): Future[Boolean] =
    whenReady("editor.executeEdits", obj("source" -> str(source), "edits" -> arr(edits.map { case (r, t) =>
      obj("range" -> r.json, "text" -> str(t))
    }*))).map(j => j.isBool && j.asBoolean)

  /** The text as it is now counted as saved: `dirty` is false until it changes again. */
  def markSaved(): Unit = savedText @= text()

  // ---- decorations and markers ----

  /** The decorations `oldIds` replaced by `decorations`: the new ones' ids. */
  def deltaDecorations(oldIds: List[String], decorations: List[MonacoDecoration]): Future[List[String]] =
    whenReady("editor.deltaDecorations", obj("oldIds" -> arr(oldIds.map(i => str(i))*),
      "newDecorations" -> arr(decorations.map(_.toJson)*))).map(j => if (j.isArr) j.asVector.toList.map(_.asString) else Nil)

  private var lit: List[String] = Nil

  /** `range` scrolled to the middle and lit (MonacoDecoration.highlight, or whole `lines`), in place of what was lit
    * this way before: a definition gone to, a usage found. `clearHighlight` puts it out. */
  def revealAndHighlight(range: MonacoRange, lines: Boolean = false): Future[Unit] = {
    val decoration = if (lines) MonacoDecoration.lines(range.startLine, range.endLine) else MonacoDecoration.highlight(range)
    revealRangeInCenter(range).flatMap(_ => deltaDecorations(lit, List(decoration))).map(ids => lit = ids)
  }

  def clearHighlight(): Future[Unit] = deltaDecorations(lit, Nil).map(_ => lit = Nil)

  /** The markers of `owner` on the editor's model replaced by `markers`. Owners keep theirs apart (a compiler's, a
    * linter's). */
  def setModelMarkers(markers: List[MonacoMarker], owner: String = "default"): Future[Unit] =
    whenReady("markers.set", obj("owner" -> str(owner), "markers" -> arr(markers.map(_.toJson)*))).map(_ => ())

  def clearModelMarkers(owner: String = "default"): Future[Unit] =
    whenReady("markers.clear", obj("owner" -> str(owner))).map(_ => ())

  // ---- actions and commands ----

  /** An action in the editor (MonacoAction); `run` is called with its id whenever it runs. Replaces one of its id. */
  def addAction(action: MonacoAction): Future[Unit] = {
    check()
    actions(action.id) = action
    // one given before its editor is made goes with the rest as it attaches
    if (isAttached) invoke("editor.addAction", action.toJson).map(_ => ()) else ready
  }

  def removeAction(id: String): Future[Unit] = {
    actions.remove(id)
    if (!isAttached) Future.unit else invoke("editor.removeAction", obj("id" -> str(id))).map(_ => ())
  }

  /** `handler` run by `keybinding` (MonacoKeyMod or'ed with a MonacoKeyCode) while `context` (a Monaco context-key
    * expression) holds, with no entry in the palette: the id `removeCommand` takes. */
  def addCommand(keybinding: Int, context: Option[String] = None)(handler: => Unit): Future[String] = {
    check()
    commandsMade += 1
    val id = "cmd-" + commandsMade.toString
    commands(id) = (keybinding, context, () => handler)
    if (isAttached) invoke("editor.addCommand", command(id)).map(_ => id) else ready.map(_ => id)
  }

  def removeCommand(commandId: String): Future[Unit] = {
    commands.remove(commandId)
    if (!isAttached) Future.unit else invoke("editor.removeCommand", obj("commandId" -> str(commandId))).map(_ => ())
  }

  /** One of Monaco's own actions run (`"editor.action.formatDocument"`, `"editor.action.commentLine"`), or one added. */
  def trigger(actionId: String, source: String = "everyui", payload: Json = Null): Future[Unit] =
    whenReady("editor.trigger", obj("source" -> str(source), "handlerId" -> str(actionId), "payload" -> payload)).map(_ => ())

  /** The find widget opened. */
  def find(): Future[Unit] = trigger("actions.find")
  /** The find widget opened for replacing. */
  def replace(): Future[Unit] = trigger("editor.action.startFindReplaceAction")
  /** The next match of what was last found. */
  def findNext(): Future[Unit] = trigger("editor.action.nextMatchFindAction")
  def undo(): Future[Unit] = trigger("undo")
  def redo(): Future[Unit] = trigger("redo")
  def formatDocument(): Future[Unit] = trigger("editor.action.formatDocument")

  // ---- models ----

  /** A model of its own (an open file), shown by `switchToModel` and kept, with its undo and markers, until
    * `disposeMonacoModel`. Given no `uri`, Monaco names it. Its uri. */
  def createModel(value: String, language: String = "plaintext", uri: Option[String] = None): Future[String] =
    whenReady("models.create", obj("value" -> str(value), "language" -> str(language),
      "uri" -> uri.map(u => str(u)).getOrElse(Null))).map(j => Wire.string(j, "uri"))

  def switchToModel(uri: String): Future[Unit] = whenReady("editor.setModel", obj("uri" -> str(uri))).map(_ => ())

  /** A model made by `createModel` let go. Switch away from it first if the editor shows it. */
  def disposeMonacoModel(uri: String): Future[Unit] = whenReady("models.dispose", obj("uri" -> str(uri))).map(_ => ())

  /** Done with: the editor let go of, and every call waiting fails. */
  def dispose(): Unit = if (!disposed) {
    val was = bridge
    val id = editorId
    detach()
    was.foreach(b => b.call("editor.dispose", obj("editorId" -> str(id))))
    disposed = true
    attachedOnce.tryFailure(new MonacoBridgeException("the controller was disposed"))
  }

  // ---- what MonacoEditor does with it ----

  /** What its editor is made with: the options, then the text, the language, the theme showing and read-only over
    * them. */
  private[monaco] def createOptions: Json = Wire.merge(opts().toJson, obj(
    "value" -> str(text()), "language" -> str(lang()),
    "theme" -> str(MonacoThemes.current.getOrElse(themed())), "readOnly" -> bool(locked())))

  /** Its editor made (`editor.create`'s answer): the actions and commands given it before are given again. */
  private[monaco] def attach(to: MonacoBridge, created: Json): Unit = {
    if (disposed) throw new IllegalStateException("MonacoController.attach on a disposed controller")
    if (isAttached) throw new IllegalStateException("MonacoController is already attached (editor " + editorId + ")")
    bridge = Some(to)
    editorId = Wire.string(created, "editorId")
    mirror = new TextMirror(Wire.string(created, "value"), Wire.int(created, "versionId"))
    model @= Wire.string(created, "uri")
    to.texts(model()) = mirror
    text @= mirror.value
    stopListening = to.listen(event => if (event.editorId == editorId) heard(event))
    live @= true
    actions.values.foreach(a => invoke("editor.addAction", a.toJson))
    commands.keys.foreach(id => invoke("editor.addCommand", command(id)))
    attachedOnce.trySuccess(())
  }

  /** Its editor gone: calls wait for the next one. */
  private[monaco] def detach(): Unit = if (isAttached) {
    stopListening()
    bridge.foreach(_.texts.remove(model()))
    bridge = None
    editorId = ""
    live @= false
    if (attachedOnce.isCompleted) attachedOnce = Promise[Unit]()
  }

  private def heard(event: BridgeEvent): Unit = {
    val p = event.payload
    event.`type` match {
      case "editor.contentChange" =>
        val change = MonacoContentChange.fromJson(p)
        text @= mirror(change)
        onDidChangeModelContent @= change
        if (!change.external) onDidChangeContent @= text()
      case "editor.modelChange" =>
        bridge.foreach(_.texts.remove(model()))
        savedBy(model()) = savedText()
        mirror = new TextMirror(Wire.string(p, "value"), Wire.int(p, "versionId"))
        model @= Wire.string(p, "uri")
        bridge.foreach(_.texts(model()) = mirror)
        text @= mirror.value
        savedText @= savedBy.getOrElse(model(), mirror.value)
      case "editor.cursorChange" =>
        val at = MonacoPosition(Wire.int(p, "line"), Wire.int(p, "column"))
        cursor @= Some(at)
        onDidChangeCursorPosition @= at
      case "editor.selectionChange" =>
        val s = p.as[MonacoSelection]
        selected @= Some(s)
        onDidChangeCursorSelection @= s
      case "editor.scrollChange" => onDidScroll @= p.as[MonacoScrollEvent]
      case "editor.focus" => onDidFocus @= ()
      case "editor.blur" => onDidBlur @= ()
      case "editor.keyDown" => onKeyDown @= p.as[MonacoKeyEvent]
      case "editor.keyUp" => onKeyUp @= p.as[MonacoKeyEvent]
      case "editor.handBack" => onHandBack @= (Wire.string(p, "key") -> Wire.bool(p, "shift"))
      case "editor.actionInvoked" =>
        val id = Wire.string(p, "actionId")
        actions.get(id).foreach(a => a.run(id))
      case "editor.commandInvoked" => commands.get(Wire.string(p, "commandId")).foreach(_._3())
      case _ => ()
    }
  }

  private def command(id: String): Json = {
    val (keybinding, context, _) = commands(id)
    obj("commandId" -> str(id), "keybinding" -> num(keybinding), "context" -> context.map(c => str(c)).getOrElse(Null))
  }

  /** `method` on this editor now: it is attached. */
  private def invoke(method: String, args: Json): Future[Json] = bridge match {
    case Some(b) => b.call(method, Wire.merge(args, obj("editorId" -> str(editorId))))
    case None => Future.failed(new MonacoBridgeException("no editor is showing this controller", method))
  }

  /** `method` on this editor once there is one. */
  private def whenReady(method: String, args: Json = obj()): Future[Json] = {
    check()
    if (isAttached) invoke(method, args) else ready.flatMap(_ => invoke(method, args))
  }

  private def check(): Unit = if (disposed) throw new IllegalStateException("MonacoController is disposed")
}

object MonacoController {
  /** A controller, named as flutter_monaco_editor's is made. */
  def apply(initialValue: String = "", language: String = "plaintext", theme: String = "vs-dark", readOnly: Boolean = false,
            options: MonacoEditorOptions = MonacoEditorOptions()): MonacoController =
    new MonacoController(initialValue, language, theme, readOnly, options)
}
