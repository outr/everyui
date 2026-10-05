package everyui.app

import everyui.app.screen.ScreenManager
import everyui.theme.Theme
import everyui.ui

/** A coach-mark tour, as NaboTV's at the end of setup (CoachTour, over CoachScreen): the screen dimmed but for what
  * each stop is about (`Spotlight`), the focus held there (`Focus.confine`), and a note beside it saying what to do.
  * Along the rail until it glides (its arrows only); OK held on the title reached for its menu (a short OK does
  * nothing), the light following it through the glide; OK on it to open it (and only that); its Play lit, where
  * nothing but Back does anything; and Ask, which any arrow lands on. Each stop is logged, as is anything pressed:
  *
  *   [ui] coach stop browse
  *   [ui] coach opened Title 9
  *   [ui] coach done
  *
  * With `probe=1` the demo walks the stops itself through the shared focus layer, the presses each stop holds back
  * among them, and prints one line to compare across platforms:
  *
  *   [ui] coach ok stops=browse,hold,open,play,ask opened=1 played=0 menus=1 on=Ask
  */
object CoachDemo extends ScreenManager {
  lazy val screen: CoachScreen = new CoachScreen

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    run(screen)
  }
}
