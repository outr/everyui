package everyui.app

import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** What runs with the app closed (ExampleBackground), at `/background` (BackgroundScreen): scheduled work, the status
  * notice and the settings pages; and `/background/opened` (BackgroundOpenedScreen), where the notice's Open lands,
  * with the app open or closed. `demo=background` opens it, and so does a launch at either path. `theme=light` shows
  * the light palette. */
object BackgroundDemo extends everyui.app.screen.ScreenManager {
  lazy val main: BackgroundScreen = new BackgroundScreen
  lazy val opened: BackgroundOpenedScreen = new BackgroundOpenedScreen

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    run(main, opened)
  }
}
