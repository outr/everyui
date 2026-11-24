package everyui.app

import everyui.background.{Background, Work}
import everyui.push.Push
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** What runs with the app closed (ExampleBackground), at `/background` (BackgroundScreen): scheduled work, the status
  * notice and the settings pages; and `/background/opened` (BackgroundOpenedScreen), where the notice's Open lands,
  * with the app open or closed. `demo=background` opens it, and so does a launch at either path. `theme=light` shows
  * the light palette; `where=1` reads where the device is from work in five seconds. */
object BackgroundDemo extends everyui.app.screen.ScreenManager {
  lazy val main: BackgroundScreen = new BackgroundScreen
  lazy val opened: BackgroundOpenedScreen = new BackgroundOpenedScreen

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    // as the app starts: its work runs while it is open where nothing runs with it closed, and a push's button that
    // started the app is heard on the next turn
    Background.register(ExampleBackground)
    // `where=1`: where the device is, read from work in 5 s (close the app meanwhile)
    if (Platform.launchArg("where").nonEmpty) Work.once("example.where", delay = 5.0)
    Push.acted.attach { a =>
      Platform.saveSetting("example.bg.push", a.id + " on \"" + a.message.title + "\" at " +
        Platform.localDateTime(System.currentTimeMillis()) + " with the app running" +
        (if (a.message.data.isEmpty) "" else ", " +
          a.message.data.toList.sorted.map((k, v) => k + "=" + v).mkString(" ")))
      Platform.log("example background: push action " + a.id + " heard by the app (Push.acted), path " +
        a.path.getOrElse("none") + ", at " + Platform.path())
    }
    run(main, opened)
  }
}
