package everyui.app

import everyui.component.{Button, Component, Container, Row}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A side drawer over a screen, as a phone app keeps its destinations: a bar's Menu opens it, in from the left over a
  * scrim, its rows made again (a turn later) each time it opens; choosing one, a press on the scrim or Back closes it.
  * `rebuild=1` makes the drawer anew for each open, the old one taken apart, as a screen laid out again does. For the
  * drive (tools/apple-drive.sh): opened, closed and opened again, `labels` lists its rows, each once, and not the
  * chips under its scrim, and `press Calendar` finds it. Logs `drawer open <n>` (the times this drawer has opened),
  * `drawer row <name> at <x>,<y> <w>x<h> shown=<shown>` for each row half a second later, and `drawer chose <row>`. */
object DrawerDemo {
  private var drawer: Option[DemoDrawer] = None

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val rebuild = Platform.launchArg("rebuild") == "1"
    val screen = new Container {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    val chips = Row(12.0)(List("Movies", "Series", "Live").map(n => new Button(n): Component)*)
    val bar = Row(12.0)()
    val menu = new Button("Menu", () => {
      val d = if (rebuild || drawer.isEmpty) made() else drawer.get
      screen.children @= Vector(bar, chips, d)
      d.open()
    })
    bar.children += menu
    bar.position.left @= 16.0
    bar.position.top @= 16.0
    chips.position.left @= 16.0
    chips.position.top @= 80.0
    screen.children @= Vector(bar, chips)
    ui.children += screen
    Platform.after(0.3, () => menu.focus())
    // the frame loop a screen manager would run, so the drawer slides in
    var last = -1.0
    everyui.task.Frames.run(_ => {
      val now = Platform.ticks()
      ui.update(if (last < 0.0) 0.0 else (now - last) / 1000.0)
      last = now
    }, () => ui.needsFrames, () => last = -1.0)
  }

  // a drawer made anew, the one before it taken apart
  private def made(): DemoDrawer = {
    val d = new DemoDrawer(ui.size.width(), ui.size.height())
    drawer.foreach(_.dispose())
    drawer = Some(d)
    d
  }
}
