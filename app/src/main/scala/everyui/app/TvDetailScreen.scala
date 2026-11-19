package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component}
import everyui.net.*
import everyui.render.Platform

/** The TV demo's second screen (TvDemo): Back returns Home. */
class TvDetailScreen extends ScrollScreen {
  override def manager: ScreenManager = TvDemo
  override def title: String = "Details"
  override def path: URLPath = path"/details"

  private val play = Button.bound("Play", () => Platform.log("tv: pressed Play"))

  override def defaultFocus(): Component = play

  override def createUI(): Task[Unit] = Task {
    body.children += play
  }

  override protected def activate(): Task[Unit] = super.activate().map(_ => Platform.log("tv: details shown"))
}
