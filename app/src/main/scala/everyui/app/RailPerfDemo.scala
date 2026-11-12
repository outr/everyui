package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform

/** A television's Home, cut down to what one move of the focus along a rail costs (`RailPerfScreen`): a hero of a
  * title, a line about it, a synopsis and a progress bar above a rail of `n=<cards>` poster cards (20 when not given),
  * each ringed as the focus lands on it, the rail gliding to keep the focus in the middle; with `rails=<k>`, k such
  * rails one above the next, scrolling up and down under the hero, as a Home of many rails. Each move is logged, and
  * every frame of 50 ms or more, with how much of it was the app's own update:
  *
  *   [ui] railperf focus 3
  *   [ui] railperf frame 312ms (update 4ms)
  *
  * OK on a card takes the rail apart and builds it again (how long the old one takes to go, `[dispose]`), or, with
  * `video=<url>`, starts that video behind the hero, and stops it at the next OK. Launched with `rokuperf=1` on a Roku,
  * the runtime says what each frame did as well (`[perf]` lines: UniversalRuntime.brs's uiPerf). */
object RailPerfDemo extends ScreenManager {
  lazy val screen: RailPerfScreen = new RailPerfScreen

  def run(): Unit = run(screen)

  override protected def frame(ms: Double, updateMs: Double): Unit =
    if (ms >= 50.0 && ms < 5000.0) Platform.log("railperf frame " + math.round(ms).toString + "ms (update " +
      math.round(updateMs).toString + "ms)")
}
