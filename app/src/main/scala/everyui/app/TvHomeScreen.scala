package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container}
import everyui.component.types.Semantics
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme

/** The TV demo's first screen (TvDemo): where the app starts, what it was launched with and the screen it has, a button
  * to the details, one beside it for the remote's pointer to come onto, one to a sign-in form, and a ruler 100 units
  * across ("ruler"), measured on the page as the screen's units are. */
class TvHomeScreen extends ScrollScreen {
  override def manager: ScreenManager = TvDemo
  override def title: String = "Home"
  override def path: URLPath = path"/"

  private val open = Button.bound("Open details", () => TvDemo.show(TvDemo.details).startUnit())
  private val settings = Button.bound("Settings", () => Platform.log("tv: pressed Settings"))
  private val signIn = Button.bound("Sign in", () => TvDemo.show(TvDemo.signIn).startUnit())
  private val ruler = new Container {
    size.width @= 100.0
    size.height @= 4.0
    backgroundColor := Theme.accent()
    semantics @= Semantics(label = "ruler")
  }

  override def defaultFocus(): Component = open

  override def createUI(): Task[Unit] = Task {
    body.children ++= List(open, settings, signIn, ruler)
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    Platform.log("tv: home shown (kind " + Platform.kind.toString + ", version " + Platform.osVersion() + ", open=" +
      Platform.launchArg("open") + ")")
    Platform.log("tv: screen " + math.round(Platform.screenWidth()).toString + "x" +
      math.round(Platform.screenHeight()).toString + " at " + Platform.pixelRatio().toString)
  }
}
