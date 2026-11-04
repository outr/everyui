package everyui.date

import everyui.render.Platform

/** The device's time zone changing (`Platform.onTimeZone`): looked at again as the app comes back to the front or its
  * window gets the focus back, where a zone changed while the app was away is first seen, and told where it is not
  * the one last seen. */
object TimeZones {
  private var known: String = ""
  private var watching: Boolean = false
  private var listeners: List[() => Unit] = Nil

  /** `f` told as the zone changes. What stops it being told. */
  def onChange(f: () => Unit): () => Unit = {
    if (!watching) {
      watching = true
      known = Platform.timeZone()
      Platform.onResume(() => check())
      Platform.onFocus(() => check())
    }
    listeners = listeners :+ f
    () => listeners = listeners.filterNot(_ eq f)
  }

  /** The zone read again, and told where it changed. */
  def check(): Unit = {
    val now = Platform.timeZone()
    if (now != known) {
      known = now
      listeners.foreach(f => f())
    }
  }
}
