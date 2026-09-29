package everyui.example

import reactify.*
import everyui.Color
import everyui.app.screen.ScreenManager
import everyui.async.Task
import everyui.component.Container
import everyui.example.ui.AnimationExample
import everyui.example.ui.component.Header
import everyui.ui

/** youi's `ExampleApp`: the shared theme colours, the header, and the content area screens are shown in. */
object ExampleApp extends ScreenManager {
  val darkMode: Var[Boolean] = Var(false)

  val textColor: Val[Color] = Val(if (darkMode()) Color.fromHex("e0e0e0") else Color.fromHex("212121"))
  val secondaryText: Val[Color] = Val(if (darkMode()) Color.fromHex("b0b0b0") else Color.fromHex("666666"))
  val accentColor: Val[Color] = Val(if (darkMode()) Color.fromHex("90caf9") else Color.fromHex("3949ab"))
  val bgColor: Val[Color] = Val(if (darkMode()) Color.fromHex("121212") else Color.White)

  /** The active screen's title, shown in the header. */
  val title: Var[String] = Var("")

  lazy val appHeader: Header = new Header

  lazy val contentContainer: Container = new Container
  override def host: Container = contentContainer

  /** The frame-rate checks (tools/check.sh, `checks fps`) read the `fps=` line from every live run. */
  override protected def reportsFps: Boolean = true

  override protected def load(): Task[Unit] = super.load().map { _ =>
    contentContainer.size.width := ui.size.width
    contentContainer.size.height := ui.size.height() - appHeader.size.height()
    // youi's content follows the header in document flow; the render seam places it explicitly
    contentContainer.position.top := appHeader.size.height()
    contentContainer.backgroundColor := bgColor
    ui.children += appHeader
    ui.children += contentContainer
  }

  lazy val animation: AnimationExample = new AnimationExample

  def main(): Unit = run(animation)
}
