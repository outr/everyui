package everyui.app

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, ButtonStyle, Column, Component, TextView}
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme

/** Where the example's status notice's Open lands (BackgroundDemo), at `/background/opened`: says so, with the way
  * back to the rest of the demo. */
class BackgroundOpenedScreen extends UIScreen {
  override def manager: ScreenManager = BackgroundDemo
  override def title: String = "Opened from the notice"
  override def path: URLPath = path"/background/opened"

  private val back = new Button("Back to the demo", () => { manager.go("/background"); () }, ButtonStyle.Filled)

  override def defaultFocus(): Component = back

  override def createUI(): Task[Unit] = Task {
    container.children += Column(12.0, padding = 16.0)(TextView("Opened from the notice", 22.0),
      TextView("The notice's Open brought the app here, at /background/opened.", 14.0, Theme.muted()), back)
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    Platform.log("example background: opened at " + Platform.path())
  }
}
