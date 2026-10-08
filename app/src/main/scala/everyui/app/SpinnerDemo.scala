package everyui.app

import everyui.component.{Container, IconButton}
import everyui.component.types.Semantics
import everyui.font.MaterialIcon
import everyui.kit.{AsyncPress, Spinner}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A button swapped for a spinner while its work runs, as an app's "Generate" button is (AsyncPress), in a panel built
  * before it is on the screen, as an app's is: the work takes `work=<seconds>` (0.42 by default, just past
  * `AsyncPress.spinAfter`), so the spinner shows, and must stay up its `minSpin` with the size it was given. A click
  * presses it, and `probe=1` presses it a second in. Each change is logged ("[ui] spinner on 401 ms", "[ui] spinner
  * done 421 ms", "[ui] spinner off 901 ms", from the press). */
object SpinnerDemo {
  private var started = 0L
  private var go: () => Unit = () => ()
  private def since: String = (System.currentTimeMillis() - started).toString + " ms"

  def run(): Unit = {
    val work = Platform.launchArg("work").toDoubleOption.getOrElse(0.42)
    val button = new IconButton(MaterialIcon.AutoAwesome, "Generate", () => go())
    val spinner = new Spinner(18.0, () => Theme.accent())
    spinner.visible @= false
    spinner.semantics @= Semantics("status", label = "Working")
    button.position.left @= 100.0
    button.position.top @= 100.0
    spinner.position.left := button.position.left() + (button.size.width() - 18.0) / 2.0
    spinner.position.top := button.position.top() + (button.size.height() - 18.0) / 2.0
    val async = new AsyncPress(on => {
      button.visible @= !on
      spinner.visible @= on
      Platform.log("spinner " + (if (on) "on " else "off ") + since)
    })
    go = () => if (!async.running) {
      started = System.currentTimeMillis()
      async.press { done =>
        Platform.after(work, () => {
          Platform.log("spinner done " + since)
          done(Right(()))
        })
      }
    }
    // in a panel built before it is on the screen, as an app's is
    spinner.tooltip @= "Working"
    val panel = new Container
    panel.size.width @= 400.0
    panel.size.height @= 300.0
    panel.children ++= List(button, spinner)
    ui.children += panel
    if (Platform.launchArg("probe") == "1") Platform.after(1.0, () => go())
  }
}
