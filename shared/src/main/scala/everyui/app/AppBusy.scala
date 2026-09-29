package everyui.app

import everyui.async.Task
import everyui.render.BusyDisplay
import reactify.*

/** Whether the app is busy, shown the way the platform shows an app at work while the person looks elsewhere: the
  * web animates the page's icon in its tab, a spinner or a ring filling with the progress, over the app's own icon;
  * the desktop fills its taskbar or dock entry where the system offers one (macOS; Linux with a Unity launcher, such
  * as KDE's); Android posts an ongoing notification with a progress bar. A Roku, an Apple TV and an Android TV show
  * nothing (`supported` false).
  *
  * {{{
  *   AppBusy.state := running().headOption.map(w => Busy(Some(done() / total()), Some(w.name)))
  * }}} */
object AppBusy {
  /** What the app is busy with, or None while it is idle, when everything is as it was: the page's icon, the title,
    * the taskbar, no notification. */
  val state: Var[Option[Busy]] = Var(None)

  /** Whether the title shows it too, "(42% deliver) " before the app's own (off by default): the web's tab and the
    * desktop's window. Where the platform shows nothing else, this is the one sign. A title the app sets while busy
    * takes the prefix at the next change. */
  val inTitle: Var[Boolean] = Var(false)

  /** How many seconds a progress may stand still before a small arc goes round its ring, so that a long stage does
    * not look stalled: None (the default) for never. The web's icon only; elsewhere the platform's own bar shows it.
    *
    * {{{
    *   AppBusy.stillAfter @= Some(3.0)
    * }}} */
  val stillAfter: Var[Option[Double]] = Var(None)

  /** Whether this platform shows the app at work, beyond the title. */
  def supported: Boolean = BusyDisplay.supported()

  /** Whether it may: an Android from 13 on asks the person before an app posts a notification. */
  def permitted: Boolean = BusyDisplay.permitted()

  /** Ask for what `permitted` needs (the system's own prompt, where there is one), and whether it was given. Best
    * asked once the person has started something long, which is when the question makes sense to them. */
  def permit(): Task[Boolean] = Task.async[Boolean](done => BusyDisplay.permit(done))

  state.attach(_ => show())
  inTitle.attach(_ => show())
  stillAfter.attach(_ => show())

  private def show(): Unit = BusyDisplay.show(state(), inTitle(), stillAfter())
}
