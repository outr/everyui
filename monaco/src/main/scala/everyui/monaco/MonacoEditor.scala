package everyui.monaco

import everyui.component.{Button, Container, Embed, TextView}
import everyui.focus.Focus
import everyui.keymap.{Chord, Command, Keymap}
import everyui.kit.CodeView
import everyui.render.Platform
import everyui.theme.Theme
import fabric.*

import scala.util.{Failure, Success}

/** Monaco, VS Code's editor, in a box the app lays out: `controller` drives it.
  *
  * {{{
  *   val code = MonacoController(initialValue = source, language = "scala")
  *   val editor = new MonacoEditor(code)
  *   editor.size.width := panel.size.width()
  *   editor.size.height := panel.size.height()
  *   editor.exit @= Some(fileTree)                    // Escape with nothing to close goes back to the tree
  * }}}
  *
  * The editor is made the first time the box is on the screen with a size, and let go of when it is disposed. It
  * follows the box's size, takes the app's focus when clicked into, and puts its caret in its text when the app's
  * focus arrives (everyui.component.Embed). Where no web engine runs it, the text shows read-only (CodeView).
  *
  * `transparent` leaves the box clear rather than the palette's surface behind the editor, for a clear theme
  * (`MonacoTheme.transparent`) over a picture. */
class MonacoEditor(val controller: MonacoController = MonacoController(), transparent: Boolean = false,
                   onChanged: String => Unit = null, owned: Boolean = false)
  extends Embed("monaco", Monaco.site, new CodeView(controller.value())) {
  import Monaco.uiThread

  private var bridge: Option[MonacoBridge] = None
  private var editorId = ""
  private var making = false
  private var disposed = false
  private lazy val loadError: Container = {
    val panel = new Container
    panel.position.left @= 16.0
    panel.position.top @= 32.0
    panel.size.width := math.max(0.0, size.width() - 32.0)
    panel.size.height @= 100.0
    val message = TextView("The editor couldn't load. Check your connection and try again.")
    message.maxWidth := panel.size.width()
    val retry = new Button("Retry editor", () => { panel.visible @= false; make() })
    retry.position.top := message.size.height() + 12.0
    panel.children ++= List(message, retry)
    children += panel
    panel
  }

  if (live) {
    if (!transparent) backgroundColor := Theme.surface()
    guestFocus = on => if (controller.isAttached) { if (on) controller.focus() else controller.blur() }
    follow(controller.onHandBack, (key: (String, Boolean)) => handBack(key._1, shift = key._2))
    if (onChanged != null) follow(controller.onDidChangeContent, (text: String) => onChanged(text))
    onSize((width, height) => if (editorId.nonEmpty) layout(width, height) else make())
    // Showing the same-sized box does not fire onSize. Monaco still needs a layout after being hidden,
    // especially when wrapping or the enclosing pane changed while its DOM had no measurable box.
    visible.attach(on => if (on) Platform.after(0.0, () => {
      if (!disposed && Focus.showing(this)) {
        if (editorId.nonEmpty) layout(size.width(), size.height()) else make()
      }
    }))
  }

  /** Whether its editor has been made. */
  def made: Boolean = editorId.nonEmpty

  // made once the box is on the screen: on the web the editor measures itself in the page
  private def make(): Unit = if (!making && !disposed && editorId.isEmpty) {
    if (!Focus.showing(this)) Platform.after(0.05, () => make())
    else {
      making = true
      val to = Monaco.bridge(this)
      to.attach(this)
      bridge = Some(to)
      // on a shared model, its copy in this Monaco first (on the web, the page's one copy), and kept alive meanwhile
      val shared = controller.startModel
      val done = shared.map(m => MonacoModels.use(m.uri)).getOrElse(() => ())
      shared.map(_.in(to)).getOrElse(scala.concurrent.Future.unit).flatMap(_ =>
        to.call("editor.create", obj("containerId" -> str(id), "options" -> controller.createOptions,
          "uri" -> shared.map(m => str(m.uri)).getOrElse(Null), "width" -> num(size.width()), "height" -> num(size.height())))
      ).onComplete {
        case Success(created) =>
          making = false
          done()
          editorId = Wire.string(created, "editorId")
          if (disposed) to.call("editor.dispose", obj("editorId" -> str(editorId)))
          else {
            loadError.visible @= false
            controller.attach(to, created)
            layout(size.width(), size.height())
            if (focused()) controller.focus()
          }
        case Failure(t) =>
          making = false
          done()
          Platform.log("[monaco] the editor could not be made: " + t.getMessage)
          if (!disposed) loadError.visible @= true
      }
    }
  }

  private def layout(width: Double, height: Double): Unit =
    bridge.foreach(_.call("editor.layout", obj("editorId" -> str(editorId), "width" -> num(width), "height" -> num(height))))

  override def dispose(): Unit = {
    if (!disposed) {
      disposed = true
      if (owned) controller.dispose()
      else if (editorId.nonEmpty) {
        controller.detach()
        bridge.foreach(_.call("editor.dispose", obj("editorId" -> str(editorId))))
      }
      bridge.foreach(_.detach(this))
    }
    super.dispose()
  }

  override def kind: String = "MonacoEditor"
}

object MonacoEditor {
  /** `save` bound to `chord` (Primary+S: Ctrl+S, Cmd+S on a Mac) in `keymap`, as a command like any other of the app's
    * (`everyui.keymap`), and left to the app while the keys are an editor's (`Keymap.embed`), where every other
    * chord is the editor's own. Bind it where the editors are: a screen's keymap, or the global one. */
  def saveCommand(keymap: Keymap = Keymap.global, chord: Chord = Chord("S", primary = true), id: String = "monaco.save",
                  label: String = "Save")(save: => Unit): Command = {
    val command = keymap.add(id, label, chord)(save)
    val kept = Keymap.embeds().getOrElse("monaco", Nil)
    if (!kept.contains(chord)) Keymap.embed("monaco", (kept :+ chord)*)
    command
  }

  /** An editor with a controller of its own, disposed with it, as flutter_monaco_editor's widget is made. */
  def apply(initialValue: String = "", language: String = "plaintext", theme: String = "vs-dark", readOnly: Boolean = false,
            options: MonacoEditorOptions = MonacoEditorOptions(), onChanged: String => Unit = null,
            transparent: Boolean = false): MonacoEditor =
    new MonacoEditor(MonacoController(initialValue, language, theme, readOnly, options), transparent, onChanged, owned = true)

  /** An editor on `model` (MonacoModels), with a controller of its own disposed with it: one more view of the document,
    * sharing its text, undo and dirty state with every other editor on it, and keeping its own cursor and scroll. */
  def of(model: MonacoModel, theme: String = "vs-dark", readOnly: Boolean = false,
         options: MonacoEditorOptions = MonacoEditorOptions(), transparent: Boolean = false): MonacoEditor =
    new MonacoEditor(MonacoController.forModel(model, theme, readOnly, options), transparent, owned = true)
}
