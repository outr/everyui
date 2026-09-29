package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** A project seen from above on a canvas that pans and zooms (ZoomCanvas): five sections of nine modules, each of eight
  * files, each of six symbols, and a sixth section that is a drawing of a network, 2,578 boxes in all, of which only
  * those in view and wide enough on the screen are made. A section shows its modules once it is 300 pixels wide, a
  * module its files at 420, a file its symbols at 220. The sections' boxes are part of the picture (they scale); their
  * names and everything in them are laid out at the size they have on the screen, so what they say stays readable at
  * every depth. A click on a box glides in to it; in the drawing a click picks a router or a link (`ZoomHit.figure`).
  * `theme=light` shows the light palette. Logs `zoom ok <items> items, <drawn> drawn` once built, and
  * `zoom clicked <path>` for each click (`ZoomScreen`). */
object ZoomDemo extends ScreenManager {
  lazy val screen: ZoomScreen = new ZoomScreen

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    run(screen)
  }
}
