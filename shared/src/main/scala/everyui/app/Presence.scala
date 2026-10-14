package everyui.app

/** Whether somebody can see the app, from the platform's own signals, told on as `Platform.away` and
  * `Platform.resumed`: away once on leaving and resumed once on coming back, however many signals say each (a page
  * hidden, then hidden again as it is left; shown, then shown again from the back/forward cache), and never a return
  * that was not a leaving first. It starts in front, so the first time the app is seen is a launch, not a return.
  *
  * {{{
  *   val presence = new Presence(() => Platform.away(), () => Platform.resumed())
  *   presence.hidden()   // away
  *   presence.hidden()   // nothing: it is away already
  *   presence.shown()    // resumed
  * }}} */
class Presence(tellAway: () => Unit, tellResumed: () => Unit) {
  private var gone = false

  /** Whether it is away now. */
  def isAway: Boolean = gone

  /** Out of sight: another app has the screen, the page is hidden or left, the device is going to sleep. */
  def hidden(): Unit = if (!gone) {
    gone = true
    tellAway()
  }

  /** In sight: resumed if it was away, and nothing if it never left. */
  def shown(): Unit = if (gone) {
    gone = false
    tellResumed()
  }
}
