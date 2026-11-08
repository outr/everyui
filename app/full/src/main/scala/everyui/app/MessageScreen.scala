package everyui.app

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, Column, Component, TextView}
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** One message of the inbox demo (InboxDemo), at `/inbox/<id>`, where a row of the widget opens the app: its id,
  * and a way back to the inbox. */
class MessageScreen extends UIScreen {
  override def manager: ScreenManager = InboxDemo
  override def title: String = "Message"
  override def path: URLPath = path"/inbox/m1"
  override def pattern: Option[String] = Some("/inbox/:id")

  private val shown = Var("")
  private val words = new TextView {
    content := shown()
    font.size @= 22.0
    color := Theme.text()
  }
  private val all = new Button("All mail", () => { manager.go("/inbox"); () })

  override def defaultFocus(): Component = all

  override def createUI(): Task[Unit] = Task {
    container.children += Column(12.0, padding = 16.0)(words, all)
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    shown @= "Message " + parameter("id").getOrElse("")
    Platform.log("inbox: " + Platform.path())
  }
}
