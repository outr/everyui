package everyui.app

import everyui.app.screen.ScreenManager

/** A rail of cards whose scroll glides with the focus, and a card changed off the thread that draws while the app is
  * away, as NaboTV's socket once changed a card: the frame loop, idle by then, was woken on that thread, and on Android
  * every glide after it stood still (`Frames`). It logs where the rail is a second after each move of the focus, and
  * the change when it is made:
  *
  *   [ui] offmain changed off the main thread
  *   [ui] offmain rail at 412
  */
object OffMainDemo extends ScreenManager {
  lazy val screen: OffMainScreen = new OffMainScreen

  def run(): Unit = run(screen)
}
