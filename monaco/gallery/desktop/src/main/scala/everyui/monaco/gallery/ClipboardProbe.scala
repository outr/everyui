package everyui.monaco.gallery

import everyui.component.Component
import everyui.monaco.{Monaco, MonacoEditor}
import everyui.render.Platform
import everyui.ui
import javafx.scene.input.{Clipboard, ClipboardContent, KeyCode}
import javafx.event.Event
import javafx.scene.input.KeyEvent
import everyui.render.Host

/** Whether the clipboard reaches Monaco in JavaFX's web view, by the keys the window hands the web view (`--probe`): text put on the system
  * clipboard pasted with Ctrl+V, and the editor's text copied back out with Ctrl+A and Ctrl+C. What it finds goes to
  * the log as `[probe]` lines. */
object ClipboardProbe {
  import Monaco.uiThread

  def run(): Unit = editor(ui).foreach { e =>
    // key events sent to the web view itself, as the window would send them: nothing reaches any other window
    def chord(key: KeyCode): Unit = {
      val target = Host.scene.getFocusOwner
      val text = key.getChar.toLowerCase
      List(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED).foreach { kind =>
        Event.fireEvent(target, new KeyEvent(kind, "", text, key, false, true, false, false))
      }
    }
    val content = new ClipboardContent
    content.putString("PASTED-FROM-THE-SYSTEM")
    Clipboard.getSystemClipboard.setContent(content)
    e.focus()
    e.controller.focus().foreach { _ =>
      Platform.after(0.5, () => {
        chord(KeyCode.V)
        Platform.after(1.0, () => {
          Platform.log("[probe] paste reached the editor: " + e.controller.value().contains("PASTED-FROM-THE-SYSTEM"))
          Clipboard.getSystemClipboard.clear()
          chord(KeyCode.A)
          chord(KeyCode.C)
          Platform.after(1.0, () => {
            val copied = Option(Clipboard.getSystemClipboard.getString).getOrElse("")
            Platform.log("[probe] copy reached the system: " + (copied.nonEmpty && e.controller.value().startsWith(copied.take(20))) +
              " (" + copied.length + " characters)")
          })
        })
      })
    }
  }

  private def editor(c: Component): Option[MonacoEditor] = c match {
    case e: MonacoEditor => Some(e)
    case _ => c.childComponents.iterator.map(editor).collectFirst { case Some(e) => e }
  }
}
