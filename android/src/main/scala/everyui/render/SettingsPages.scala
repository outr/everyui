package everyui.render

import android.content.{ActivityNotFoundException, Intent}
import android.net.Uri
import android.provider.Settings
import everyui.SettingsPage
import everyui.activity.ServiceRuntime
import java.util.concurrent.atomic.AtomicBoolean

/** The system's settings pages (`everyui.SystemSettings`) on Android, opened from the activity: the app's details
  * (`App`, and `AppLocation`, as Android has no page for one app's location alone), its notifications, its "Pause app
  * activity if unused" switch (ACTION_AUTO_REVOKE_PERMISSIONS, Android 11 and later), the device's location switch.
  * A page the device lacks (a television's settings) opens the app's details instead. The answer comes as the
  * person returns to the app. */
object SettingsPages {
  def open(page: SettingsPage, done: Boolean => Unit): Unit = {
    val activity = Host.activity
    if (activity == null) {
      Platform.log("settings: " + page.toString + " not opened: there is no window to open it from")
      done(false)
    }
    else {
      val app = Uri.parse("package:" + activity.getPackageName)
      val details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
      val wanted = page match {
        case SettingsPage.App | SettingsPage.AppLocation => details
        case SettingsPage.AppNotifications =>
          new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,
            activity.getPackageName)
        case SettingsPage.UnusedAppRestrictions => new Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, app)
        case SettingsPage.DeviceLocation => new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
      }
      val opened = List(wanted, details).distinct.exists { intent =>
        try {
          activity.startActivity(intent)
          true
        }
        catch { case _: ActivityNotFoundException => false }
      }
      Platform.log("settings: " + page.toString + (if (opened) " opened" else " not opened: the device has no page"))
      if (!opened) done(false)
      else {
        // answered as the person comes back
        val once = new AtomicBoolean(false)
        var stop: () => Unit = () => ()
        stop = Platform.onResume(() => if (once.compareAndSet(false, true)) {
          stop()
          done(true)
        })
      }
    }
  }

  def unusedAppRestrictions(done: Boolean => Unit): Unit = done(
    // Android 11 takes an unused app's permissions away; nothing does before it
    android.os.Build.VERSION.SDK_INT >= 30 &&
      (try !ServiceRuntime.context.getPackageManager.isAutoRevokeWhitelisted catch { case _: Throwable => false }))
}
