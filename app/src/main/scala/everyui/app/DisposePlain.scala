package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component, TextView}
import everyui.net.*
import everyui.theme.Theme

/** A screen of a heading and one button, kept for the app's life: the dispose demo's home and its calendar. */
class DisposePlain(val manager: ScreenManager, name: String, where: String) extends ScrollScreen {
  override def title: String = name
  override def path: URLPath = URLPath(where)

  private val button = new Button(name)

  override def defaultFocus(): Component = button

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor := Theme.background()
    body.children ++= List(TextView(name, 24.0), button)
  }
}
