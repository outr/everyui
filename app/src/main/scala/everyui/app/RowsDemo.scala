package everyui.app

import everyui.app.screen.ScreenManager

/** A table of `n=<count>` rows (200 when not given), four words and a duration to a row, then a hundred more rows
  * added one at a time: how long each takes to be laid out, with its sizes read, measured from the start to the turn
  * it settles. Then it stays as it is, or, with `stream=<pushes a second>`, takes pushes as a live socket brings them
  * (`RowsScreen`). An app run by a screen manager, as a dashboard is, so its frame loop is the one an app has. It
  * prints (tools/check.sh, or a browser's console):
  *
  *   [ui] rows 200 built in 41.0 ms
  *   [ui] rows 100 appended in 12.0 ms
  */
object RowsDemo extends ScreenManager {
  lazy val screen: RowsScreen = new RowsScreen

  def run(): Unit = run(screen)
}
