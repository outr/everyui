package everyui.render

import everyui.app.Busy

/** The app at work (`everyui.app.AppBusy`). A television has no tab, taskbar or notification to show it in, and no
  * title on the screen, so this platform shows nothing and says so. */
object BusyDisplay {
  def supported(): Boolean = false

  def permitted(): Boolean = false

  def permit(done: Boolean => Unit): Unit = done(false)

  def show(busy: Option[Busy], inTitle: Boolean, still: Option[Double]): Unit = ()
}
