package everyui.app

import everyui.app.screen.ScreenManager
import everyui.component.Component
import everyui.render.Platform
import everyui.theme.Theme

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
  * Roku, the runtime says what each frame did as well (`[perf]` lines: UniversalRuntime.brs's uiPerf).
  * `dump=1` logs the page as it stands two seconds after it is shown, each component with its place, its size and its
  * measured size (`[ui] pageopen tree ...`), to compare a change that should leave the page as it was. */
object PageOpenDemo extends ScreenManager {
  lazy val start: PageStartScreen = new PageStartScreen

  def run(): Unit = {
    // in a bundled face, measured as a television app's words are (the device's own measures nothing in brs-engine)
    Theme.family @= "Open Sans"
    run(start)
    val opens = Platform.launchArg("auto").toIntOption.getOrElse(0)
    if (opens > 0) Platform.after(4.0, () => auto(opens))
  }

  /** A page opened, and closed again three seconds later. */
  def open(): Unit = {
    val page = new PageOpenScreen
    show(page).map { _ =>
      if (Platform.launchArg("dump") == "1") Platform.after(2.0, () => tree(page.root, ""))
    }.startUnit()
  }

  private def tree(c: Component, indent: String): Unit = {
    Platform.log("pageopen tree " + indent + c.kind + " x=" + c.position.left().toString + " y=" +
      c.position.top().toString + " w=" + c.size.width().toString + " h=" + c.size.height().toString + " mw=" +
      c.measured.width().toString + " mh=" + c.measured.height().toString)
    c.childComponents.foreach(child => tree(child, indent + "  "))
  }

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
