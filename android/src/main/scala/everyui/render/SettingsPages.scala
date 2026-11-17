package everyui.render

import android.app.Activity
import android.content.{ActivityNotFoundException, Intent}
import android.net.Uri
import android.os.Build
import android.provider.Settings
import everyui.SettingsPage
import everyui.activity.ServiceRuntime
import java.util.concurrent.atomic.AtomicBoolean

/** The system's settings pages (`everyui.SystemSettings`) on Android, opened from the activity: the app's details
  * (`App`, and `AppLocation`, as Android has no page for one app's location alone), its notifications, its "Pause app
  * activity if unused" switch (ACTION_AUTO_REVOKE_PERMISSIONS, Android 11 and later), the device's location switch,
  * and one notification channel's page (`Push.openChannelSettings`). A page the device lacks (a television's settings)
  * opens the app's details instead. The answer comes as the person returns to the app. */
object SettingsPages {
  def open(page: SettingsPage, done: Boolean => Unit): Unit = start(page.toString, done) { (activity, app) =>
    page match {
      case SettingsPage.App | SettingsPage.AppLocation => Nil
      case SettingsPage.AppNotifications => List(notifications(activity))
      case SettingsPage.UnusedAppRestrictions => List(new Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, app))
      case SettingsPage.DeviceLocation => List(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
  }

  /** The page of the channel `id` where it is `made` (Android 8 and later), else the app's notifications. */
  def channel(id: String, made: Boolean, done: Boolean => Unit): Unit =
    start("channel " + id, done) { (activity, _) =>
      val channel =
        if (made && Build.VERSION.SDK_INT >= 26) List(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName).putExtra(Settings.EXTRA_CHANNEL_ID, id))
        else Nil
      channel ::: List(notifications(activity))
    }

  def unusedAppRestrictions(done: Boolean => Unit): Unit = done(
    // Android 11 takes an unused app's permissions away; nothing does before it
    Build.VERSION.SDK_INT >= 30 &&
      (try !ServiceRuntime.context.getPackageManager.isAutoRevokeWhitelisted catch { case _: Throwable => false }))

  // the app's notifications: Android 8's action, which Android 5 to 7 open too given the package and uid their way
  private def notifications(activity: Activity): Intent = {
    val app = activity.getPackageName
    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app)
      .putExtra("app_package", app).putExtra("app_uid", activity.getApplicationInfo.uid)
  }

  /** The first of the pages `wanted` (for the activity and the app's package URI) the device has, else the app's
    * details, opened; answered as the person comes back, or false at once where none opened. */
  private def start(label: String, done: Boolean => Unit)(wanted: (Activity, Uri) => List[Intent]): Unit = {
    val activity = Host.activity
    if (activity == null) {
      Platform.log("settings: " + label + " not opened: there is no window to open it from")
      done(false)
    }
    else {
      val app = Uri.parse("package:" + activity.getPackageName)
      val details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
      val opened = (wanted(activity, app) :+ details).exists { intent =>
        try {
          activity.startActivity(intent)
          true
        }
        catch { case _: ActivityNotFoundException => false }
      }
      Platform.log("settings: " + label + (if (opened) " opened" else " not opened: the device has no page"))
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
}
