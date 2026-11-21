package everyui.app

import everyui.app.screen.ScreenManager

/** Browse kept between visits, its grid read again on each (`demo=browse`, BrowseDemoScreen): OK on a card opens
  * another screen, and Back comes back to Browse, whose cards are replaced, first by words for loading and a second
  * later by new ones, while the focus was on one of the old. Each move of the focus is logged ("browse: focus on …"),
  * with whether a remote can use where it is, and the app's own check after the grid fills. */
object BrowseDemo extends ScreenManager {
  lazy val other: DisposePlain = new DisposePlain(this, "A title", "/title")
  lazy val browse: BrowseDemoScreen = new BrowseDemoScreen(this, () => show(other).startUnit())

  def run(): Unit = run(browse, other)
}
