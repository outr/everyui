package universal.example

import reactify.*
import universal.Color
import universal.app.screen.ScreenManager
import universal.async.Task
import universal.component.Container
import universal.example.ui.AnimationExample
import universal.example.ui.component.Header
import universal.ui

/** youi's `ExampleApp`: the shared theme colours, the header, and the content area screens are shown in. */
object ExampleApp extends ScreenManager:
  val darkMode: Var[Boolean] = Var(false)

  val textColor: Val[Color] = Val(if darkMode() then Color.fromHex("e0e0e0") else Color.fromHex("212121"))
  val secondaryText: Val[Color] = Val(if darkMode() then Color.fromHex("b0b0b0") else Color.fromHex("666666"))
  val accentColor: Val[Color] = Val(if darkMode() then Color.fromHex("90caf9") else Color.fromHex("3949ab"))
  val bgColor: Val[Color] = Val(if darkMode() then Color.fromHex("121212") else Color.White)

  /** The active screen's title, shown in the header. */
  val title: Var[String] = Var("")

  lazy val appHeader: Header = new Header

  lazy val contentContainer: Container = new Container

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
