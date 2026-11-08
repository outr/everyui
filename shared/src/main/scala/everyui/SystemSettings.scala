package everyui

import everyui.async.Task
import everyui.render.BackgroundAccess

/** The platform's own settings, opened at a page for the person to change something only they can (the app's
  * location permission, its notifications), and whether the platform may restrict the app after months unused.
  *
  * Android opens each page (`AppLocation` is the app's own page there, its permissions a tap away: Android has no
  * page for one app's location alone). The web, the desktop, an Apple TV and a Roku have no such pages to open: there
  * `open` answers false, and `unusedAppRestrictions` false.
  *
  * {{{
  *   SystemSettings.unusedAppRestrictions.flatMap { on =>
  *     if (on) SystemSettings.open(SettingsPage.UnusedAppRestrictions) else Task.pure(false)
  *   }.start()
  * }}} */
object SystemSettings {
  /** The settings at `page` opened, and whether they were: answered as the person comes back to the app, so it reads
    * what they changed there. False where the platform has no such page, or where there is no window to open it from
    * (a `BackgroundApp`'s handler); a page the device lacks opens the app's own page instead. */
  def open(page: SettingsPage): Task[Boolean] = Task.async[Boolean](done => BackgroundAccess.openSettings(page, done))

  /** Whether the platform may take away the app's permissions and stop its background work once it has gone unused
    * for months (Android 11's automatic permission removal, 12's "Pause app activity if unused"): the person turns it
    * off on `SettingsPage.UnusedAppRestrictions`. False where there is no such thing. */
  def unusedAppRestrictions: Task[Boolean] =
    Task.async[Boolean](done => BackgroundAccess.unusedAppRestrictions(done))
}
