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
  * (`onDidChangeContent.attach(...)`). The text is kept here edit by edit, so reading it costs nothing.
  *
  * Given `initialModel` (`MonacoController.forModel`), its editor is made on that shared model (MonacoModels) rather
  * than a text of its own; `switchToModel` moves it to another, and `saved`, `dirty` and `markSaved` are then the
  * model's, shared with every editor showing it. */
class MonacoController(initialValue: String = "", initialLanguage: String = "plaintext", initialTheme: String = "vs-dark",
                       initialReadOnly: Boolean = false, initialOptions: MonacoEditorOptions = MonacoEditorOptions(),
                       initialModel: Option[MonacoModel] = None) {
  import Monaco.uiThread

  // read once, plainly: a Var given an expression of other values would follow them
  private val startText: String = initialModel.map(_.value()).getOrElse(initialValue)
  private val text = Var(startText)
  private val startLanguage: String = initialModel.map(_.language()).getOrElse(initialLanguage)
  private val lang = Var(startLanguage)
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
  private val savedText = Var(startText)
  // what was saved of each model shown before, so switching back to a file keeps whether it is dirty
  private val savedBy = scala.collection.mutable.Map.empty[String, String]
  // the shared model (MonacoModels) the editor shows, if it shows one
  private val sharedModel = Var[Option[MonacoModel]](initialModel)
  // the shared model's uri to make the next editor on, kept across a detach
  private var lastShared: Option[String] = initialModel.map(_.uri)
  /** The shared model (MonacoModels) the editor shows, if it shows one rather than a model of its own. */
  val shownModel: Val[Option[MonacoModel]] = sharedModel
  /** The text as it was last saved (`markSaved`): the text it was made with until then. */
  val saved: Val[String] = Val(sharedModel().map(_.saved()).getOrElse(savedText()))
  /** Whether the text differs from what was saved: undone back to it, it is clean again. */
  val dirty: Val[Boolean] = Val(sharedModel() match {
    case Some(m) => m.dirty()
    case None => text() != savedText()
  })

  /** The user changed the text (not the app, through `setValue`): the new text. */
  val onDidChangeContent: Channel[String] = Channel[String]
  /** Any change to the text, the app's own included, as the edits themselves. */
  val onDidChangeModelContent: Channel[MonacoContentChange] = Channel[MonacoContentChange]
  val onDidChangeCursorPosition: Channel[MonacoPosition] = Channel[MonacoPosition]
  val onDidChangeCursorSelection: Channel[MonacoSelection] = Channel[MonacoSelection]
  /** The cursor moved, with why: typed, clicked, a jump within the file, the app's own move, an edit made elsewhere
    * (MonacoCursorChange). Heard with `onDidChangeCursorPosition`, after it. */
  val onDidChangeCursor: Channel[MonacoCursorChange] = Channel[MonacoCursorChange]
  /** The selection changed, with why (MonacoSelectionChange). Heard with `onDidChangeCursorSelection`, after it. */
  val onDidChangeSelection: Channel[MonacoSelectionChange] = Channel[MonacoSelectionChange]
  val onDidScroll: Channel[MonacoScrollEvent] = Channel[MonacoScrollEvent]
  val onDidFocus: Channel[Unit] = Channel[Unit]
  val onDidBlur: Channel[Unit] = Channel[Unit]
  val onKeyDown: Channel[MonacoKeyEvent] = Channel[MonacoKeyEvent]
  val onKeyUp: Channel[MonacoKeyEvent] = Channel[MonacoKeyEvent]
  /** A key the editor had and gave back (Escape with nothing to close, Tab where nothing can be typed), and whether
    * Shift was held: what MonacoEditor hands on to the app's focus. */
  val onHandBack: Channel[(String, Boolean)] = Channel[(String, Boolean)]
  /** Go to definition, or a peek's "open", into a file this editor does not show, when no opener registered with
    * MonacoLanguages took it: the app opens the file its own way (switchToModel, a tab of its own) and reveals the
    * range. Listened to, it counts as handled; with nobody listening, Monaco does what it does by itself. */
  val onOpenLocation: Channel[MonacoLocation] = Channel[MonacoLocation]

  private var bridge: Option[MonacoBridge] = None
  private var editorId: String = ""
  private var mirror = new TextMirror(startText, 0)
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
    sharedModel() match {
      case Some(m) => m.setLanguage(language)
      case None => if (!isAttached) Future.unit else invoke("editor.setLanguage", obj("language" -> str(language))).map(_ => ())
    }
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
  def markSaved(): Unit = sharedModel() match {
    case Some(m) => m.markSaved()
    case None => savedText @= text()
  }

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
    * `disposeMonacoModel`. Given no `uri`, Monaco names it; a model already made with that uri (by an earlier call, or
    * for a peek by the model resolver) is the one, kept as it is, with its edits and undo. Its uri. */
  def createModel(value: String, language: String = "plaintext", uri: Option[String] = None): Future[String] =
    whenReady("models.create", obj("value" -> str(value), "language" -> str(language),
      "uri" -> uri.map(u => str(u)).getOrElse(Null))).map(j => Wire.string(j, "uri"))

  /** The editor shows `uri`: a model opened through MonacoModels (shared with every other editor showing it), or one
    * made by `createModel`. The editor keeps its own cursor, selection, scroll and folding in each model it has shown,
    * and has them back as it shows that model again. */
  def switchToModel(uri: String): Future[Unit] = MonacoModels.get(uri) match {
    case Some(m) =>
      check()
      val done = MonacoModels.use(uri)
      val switched = (if (isAttached) Future.unit else ready).flatMap { _ =>
        bridge match {
          case Some(to) => m.in(to).flatMap(_ => invoke("editor.setModel", obj("uri" -> str(uri))))
          case None => Future.failed(new MonacoBridgeException("no editor is showing this controller", "editor.setModel"))
        }
      }.map(_ => ())
      switched.onComplete(_ => done())
      switched
    case None => whenReady("editor.setModel", obj("uri" -> str(uri))).map(_ => ())
  }

  /** A model made by `createModel` let go (switch away from it first if the editor shows it); one opened through
    * MonacoModels is closed there (`MonacoModels.close`), and goes once no editor shows it. */
  def disposeMonacoModel(uri: String): Future[Unit] =
    if (MonacoModels.get(uri).nonEmpty) {
      MonacoModels.close(uri)
      Future.unit
    }
    else whenReady("models.dispose", obj("uri" -> str(uri))).map(_ => ())

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

  /** The shared model its next editor is made on, if it shows one: the one it was made for, or showed last. */
  private[monaco] def startModel: Option[MonacoModel] = lastShared.flatMap(MonacoModels.get)

  private[monaco] def textMirror: TextMirror = mirror

  // the editor shows `uri` now: whether it is a shared model, and that one
  private def showing(uri: String): Unit = {
    model @= uri
    sharedModel @= MonacoModels.get(uri)
    if (sharedModel().nonEmpty) lastShared = Some(uri)
  }

  /** Its editor made (`editor.create`'s answer): the actions and commands given it before are given again. */
  private[monaco] def attach(to: MonacoBridge, created: Json): Unit = {
    if (disposed) throw new IllegalStateException("MonacoController.attach on a disposed controller")
    if (isAttached) throw new IllegalStateException("MonacoController is already attached (editor " + editorId + ")")
    bridge = Some(to)
    editorId = Wire.string(created, "editorId")
    mirror = new TextMirror(Wire.string(created, "value"), Wire.int(created, "versionId"))
    showing(Wire.string(created, "uri"))
    to.show(model(), mirror)
    text @= mirror.value
    stopListening = to.listen(event => if (event.editorId == editorId) heard(event))
    to.controllers(editorId) = this
    live @= true
    actions.values.foreach(a => invoke("editor.addAction", a.toJson))
    commands.keys.foreach(id => invoke("editor.addCommand", command(id)))
    attachedOnce.trySuccess(())
  }

  /** Its editor gone: calls wait for the next one. */
  private[monaco] def detach(): Unit = if (isAttached) {
    stopListening()
    bridge.foreach(_.controllers.remove(editorId))
    bridge.foreach(_.unshow(model(), mirror))
    bridge = None
    editorId = ""
    live @= false
    if (attachedOnce.isCompleted) attachedOnce = Promise[Unit]()
    MonacoModels.release(model())
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
        val was = model()
        bridge.foreach(_.unshow(was, mirror))
        if (sharedModel().isEmpty) savedBy(was) = savedText()
        mirror = new TextMirror(Wire.string(p, "value"), Wire.int(p, "versionId"))
        showing(Wire.string(p, "uri"))
        bridge.foreach(_.show(model(), mirror))
        text @= mirror.value
        savedText @= savedBy.getOrElse(model(), mirror.value)
        MonacoModels.release(was)
      case "editor.cursorChange" =>
        val at = MonacoPosition(Wire.int(p, "line"), Wire.int(p, "column"))
        cursor @= Some(at)
        onDidChangeCursorPosition @= at
        onDidChangeCursor @= MonacoController.cursorChange(at, p)
      case "editor.selectionChange" =>
        val s = p.as[MonacoSelection]
        selected @= Some(s)
        onDidChangeCursorSelection @= s
        onDidChangeSelection @= MonacoController.selectionChange(s, p)
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

  /** A location to open: to whoever listens (onOpenLocation), and whether anyone did. */
  private[monaco] def opened(location: MonacoLocation): Boolean = {
    val listening = onOpenLocation.reactions().nonEmpty
    if (listening) onOpenLocation @= location
    listening
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

  /** A controller whose editor is made on `model` (MonacoModels), shared with every other editor showing it. */
  def forModel(model: MonacoModel, theme: String = "vs-dark", readOnly: Boolean = false,
               options: MonacoEditorOptions = MonacoEditorOptions()): MonacoController =
    new MonacoController(initialValue = model.value(), initialLanguage = model.language(), initialTheme = theme,
      initialReadOnly = readOnly, initialOptions = options, initialModel = Some(model))

  /** The guest's `editor.cursorChange` read: the cursor at `at`, and the rest of what it says. One that does not say
    * what moved it is taken for the app's move ("api"). */
  private[monaco] def cursorChange(at: MonacoPosition, p: Json): MonacoCursorChange = MonacoCursorChange(at,
    p.get("secondaryPositions").filter(_.isArr).map(_.asVector.toList.map(j =>
      MonacoPosition(Wire.int(j, "line"), Wire.int(j, "column")))).getOrElse(Nil),
    source(p), MonacoCursorReason.fromWire(Wire.int(p, "reason")))

  /** The guest's `editor.selectionChange` read, its selection `s`. */
  private[monaco] def selectionChange(s: MonacoSelection, p: Json): MonacoSelectionChange = MonacoSelectionChange(s,
    p.get("secondarySelections").filter(_.isArr).map(_.asVector.toList.map(_.as[MonacoSelection])).getOrElse(Nil),
    source(p), MonacoCursorReason.fromWire(Wire.int(p, "reason")))

  private def source(p: Json): String = {
    val s = Wire.string(p, "source")
    if (s.isEmpty) MonacoCursorChange.Api else s
  }
}
