package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform

/** How long a title's page takes to open (`PageOpenScreen`, about 200 components: a hero of words and buttons, a row of
  * tabs and `rails=<k>` rails of `n=<cards>` cards, 4 of 12 when not given) from a screen that opens one on OK
  * (`PageStartScreen`), as a Home opens a title: built afresh each time, and taken apart a moment after Back. Each open
  * is timed by the screen manager, and every frame of 50 ms or more is logged with how much of it was the app's own
  * update:
  *
  *   [ui] screen PageOpenScreen shown in 640ms
  *   [ui] pageopen frame 410ms (update 3ms)
  *
  * `auto=<opens>` opens and closes it that many times by itself, a few seconds apart. Launched with `rokuperf=1` on a
  * Roku, the runtime says what each frame did as well (`[perf]` lines: UniversalRuntime.brs's uiPerf). */
object PageOpenDemo extends ScreenManager {
  lazy val start: PageStartScreen = new PageStartScreen

  def run(): Unit = {
    run(start)
    val opens = Platform.launchArg("auto").toIntOption.getOrElse(0)
    if (opens > 0) Platform.after(4.0, () => auto(opens))
  }

  /** A page opened, and closed again three seconds later. */
  def open(): Unit = show(new PageOpenScreen).startUnit()

  private def auto(left: Int): Unit = {
    open()
    Platform.after(3.0, () => {
      back()
      if (left > 1) Platform.after(3.0, () => auto(left - 1))
    })
  }

  override protected def frame(ms: Double, updateMs: Double): Unit =
    if (ms >= 50.0 && ms < 5000.0) Platform.log("pageopen frame " + math.round(ms).toString + "ms (update " +
      math.round(updateMs).toString + "ms)")
}
