package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component}
import everyui.net.*

/** The page demo's first screen (`PageOpenDemo`): OK on its button opens a title's page. */
class PageStartScreen extends ScrollScreen {
  override def manager: ScreenManager = PageOpenDemo
  override def title: String = "Start"
  override def path: URLPath = path"/pagestart"

  private val open = new Button("Open a title", () => PageOpenDemo.open())

  override def defaultFocus(): Component = open

  override def createUI(): Task[Unit] = Task {
    body.children += open
  }
}
