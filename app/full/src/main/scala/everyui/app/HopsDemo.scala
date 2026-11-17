package everyui.app

import everyui.app.screen.ScreenManager

/** Components changed from rapid's threads, as an app's answers change them: a line of words and a column of
  * numbered chips changed every 150 ms from a `Task.sleep`'s continuation, never on the thread that draws (`Hop`). Each
  * twentieth step logs what the column holds, which is what the screen shows:
  *
  *   [ui] hops 40 on rapid-v2-worker: 29 30 … 40
  *
  * and a debug build logs where each change was made (`hop: …`). `n=<steps>` (200), `onscreen=1` makes every change
  * on the thread that draws instead (`onScreen`), and logs no hop. */
object HopsDemo extends ScreenManager {
  lazy val screen: HopsScreen = new HopsScreen

  def run(): Unit = run(screen)
}
