package everyui.app

import everyui.app.screen.ScreenManager
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui


/** A workspace's sidebar as stacked panes (StackedPanes): Folders (a file tree, with New folder, Refresh and Collapse
  * folders on its header), Conversations (a list of them, its New conversation always shown) and Log (what was done,
  * with Clear), each folding to its header and sharing the height by the handles between them. What the app would keep
  * (each pane folded or not, and its weight) is shown live at the top of the log and beside the panes. Wide, the panes
  * are 360 wide beside what was chosen (`sidebar=1`: 280); narrow, they fill the screen, as a phone's drawer.
  * `theme=light`, `rtl=1`, `collapsed=<folders,chats,log>`, `weights=<w,w,w>`, and `probe=1` folds and opens them by the keys itself.
  * Logs `panes ok` once built, `panes state ...` as what is kept changes, and each thing done. */
object PanesDemo extends ScreenManager {
  lazy val screen: PanesScreen = new PanesScreen

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    run(screen)
  }
}
