package everyui.app

import everyui.component.{Container, IconButton, ScrollContainer, TextView}
import everyui.font.MaterialIcon
import everyui.kit.{BusyRing, Spinner}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** Endless ambient motions (`Component.motion`) over a still screen, to measure what they cost while nothing else moves
  * (`--es idleAudit 1`): one busy IconButton breathing by default; `motions=5` adds a turning busy glyph, a spinner
  * and two busy rings, as an app's rail and drawer show them. `scrolled=1` puts the motions at the foot of a
  * ScrollContainer, in one tall child (so the scroll does not hide them), out of its view, and `back=<seconds>`
  * scrolls them into it that long after. */
object AmbientDemo {
  def run(): Unit = {
    val many = Platform.launchArg("motions") == "5"
    val scrolled = Platform.launchArg("scrolled") == "1"
    val words = new TextView
    words.content @= "Ambient motions: " + (if (many) "5" else "1") + (if (scrolled) ", scrolled out of view" else "")
    words.position.left @= 24.0
    words.position.top @= 24.0
    val row = new Container {
      size.width @= 340.0
      size.height @= 60.0
    }
    val queue = new IconButton(MaterialIcon.Queue, "Queue")
    queue.busy @= true
    row.children += queue
    if (many) {
      val sync = new IconButton(MaterialIcon.Sync, "Sync")
      sync.busy @= true
      sync.position.left @= 60.0
      val spinner = new Spinner(18.0, () => Theme.accent())
      spinner.position.left @= 120.0
      spinner.position.top @= 8.0
      row.children ++= List(sync, spinner, ring(170.0), ring(240.0))
    }
    ui.children += words
    if (scrolled) {
      val page = new ScrollContainer
      page.position.top @= 80.0
      page.size.width @= 360.0
      page.size.height @= 300.0
      // in one tall child, which stays in the view (the scroll hides a child wholly out of it), at its foot
      val tall = new Container {
        size.width @= 360.0
        size.height @= 1300.0
      }
      row.position.top @= 1200.0
      tall.children += row
      page.content.size.width @= 360.0
      page.content.size.height @= 1300.0
      page.content.children += tall
      ui.children += page
      Platform.launchArg("back").toDoubleOption.foreach(s => Platform.after(s, () => page.offset @= 1000.0))
    }
    else {
      row.position.left @= 24.0
      row.position.top @= 80.0
      ui.children += row
    }
  }

  // an avatar's disc with a busy ring around it
  private def ring(left: Double): Container = new Container {
    position.left @= left
    position.top @= 6.0
    size.width @= 36.0
    size.height @= 36.0
    radius @= 18.0
    backgroundColor := Theme.raised()
    // the ring goes around the disc, outside its box
    clipsToBox @= Some(false)
    children += new BusyRing(36.0, true)
  }
}
