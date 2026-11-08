package everyui.render

import everyui.SettingsPage
import everyui.background.WorkRequest
import everyui.push.OngoingNotice

/** What runs with the app closed (`everyui.background`), ongoing notices (`everyui.push.Ongoing`) and the settings
  * pages (`everyui.SystemSettings`). A page runs nothing once it is closed, and has no ongoing notices or settings
  * pages of its own to open: nothing is scheduled or shown, and every answer is no. */
object BackgroundAccess {
  def supported(): Boolean = false

  def schedule(request: WorkRequest): Boolean = false

  def cancel(key: String): Unit = ()

  def pending(key: String): Boolean = false

  def ongoingSupported(): Boolean = false

  def showOngoing(notice: OngoingNotice): Boolean = false

  def clearOngoing(key: String): Unit = ()

  def openSettings(page: SettingsPage, done: Boolean => Unit): Unit = done(false)

  def unusedAppRestrictions(done: Boolean => Unit): Unit = done(false)
}
