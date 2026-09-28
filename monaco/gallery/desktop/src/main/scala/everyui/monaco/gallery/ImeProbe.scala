package everyui.monaco.gallery

import everyui.component.Component
import everyui.monaco.{Monaco, MonacoEditor}
import everyui.render.{Host, Platform}
import everyui.ui
import javafx.collections.FXCollections
import javafx.event.Event
import javafx.scene.input.{InputMethodEvent, InputMethodTextRun, InputMethodHighlight}

/** Whether an input method's composition reaches Monaco in JavaFX's web view (`--ime`): the events an input method
  * sends, a word composed and then committed, handed to the web view itself as the window would hand them, so nothing
  * goes to any other window or to the system. What it finds goes to the log as `[ime]` lines. */
object ImeProbe {
  import Monaco.uiThread

  def run(): Unit = editor(ui).foreach { e =>
    e.focus()
    e.controller.focus().foreach { _ =>
      Platform.after(0.5, () => {
        val target = Host.scene.getFocusOwner
        def send(composed: String, committed: String): Unit = {
          val runs = FXCollections.observableArrayList[InputMethodTextRun]()
          if (composed.nonEmpty) runs.add(new InputMethodTextRun(composed, InputMethodHighlight.UNSELECTED_RAW))
          Event.fireEvent(target, new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, runs, committed,
            composed.length))
        }
        Platform.log("[ime] composing in " + target.getClass.getSimpleName)
        send("にほ", "")
        Platform.after(0.3, () => {
          send("にほん", "")
          Platform.after(0.3, () => {
            send("", "日本")
            Platform.after(1.0, () => {
              val text = e.controller.value()
              Platform.log("[ime] committed text in the editor: " + text.contains("日本") +
                "; composition left behind: " + text.contains("にほ"))
            })
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
