package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component}
import everyui.net.*
import everyui.render.Platform

/** The TV demo's first screen (TvDemo): where the app starts, what it was launched with, a button to the details and
  * one beside it, for the remote's pointer to come onto. */
class TvHomeScreen extends ScrollScreen {
  override def manager: ScreenManager = TvDemo
  override def title: String = "Home"
  override def path: URLPath = path"/"

  private val open = Button.bound("Open details", () => TvDemo.show(TvDemo.details).startUnit())
  private val settings = Button.bound("Settings", () => Platform.log("tv: pressed Settings"))

  override def defaultFocus(): Component = open

  override def createUI(): Task[Unit] = Task {
    body.children ++= List(open, settings)
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    Platform.log("tv: home shown (kind " + Platform.kind.toString + ", version " + Platform.osVersion() + ", open=" +
      Platform.launchArg("open") + ")")
  }
}
