package everyui.render

import android.app.{ActivityManager, Notification, NotificationChannel, NotificationManager, PendingIntent}
import android.content.Intent
import android.os.Build
import everyui.Permission
import everyui.push.Notice
import reactify.*

import scala.collection.mutable

/** Notifications the app shows itself (`Platform.showNotice`), on the channel push notifications use ("everyui.push",
  * "Notifications"), so the person has one switch for both. A tag is the notification's tag, so a later one with the
  * same tag replaces it. A tap brings the app to the front with the notice's key, and the activity's new intent runs
  * what the tap does ([[tapped]]); one tapped after the app was closed only opens it. Not on a television, which shows
  * no notifications of this kind; Android 13 and later ask the person first (POST_NOTIFICATIONS in the manifest). */
object Notices {
  private val channel = "everyui.push"
  private val notifications = "android.permission.POST_NOTIFICATIONS"

  def supported(): Boolean = !Platform.remoteNavigation()

  /** Read again whenever the app comes back to the front, from the settings or anywhere else. */
  lazy val permission: Var[Permission] = {
    val state = Var[Permission](read())
    Platform.onResume(() => state @= read())
    state
  }

  private def manager: NotificationManager = Host.activity.getSystemService(classOf[NotificationManager])

  private def read(): Permission =
    if (!supported()) Permission.Refused
    else if (Build.VERSION.SDK_INT >= 33 && !Permissions.held(notifications)) Permissions.state(notifications)
    else if (manager.areNotificationsEnabled) Permission.Granted
    else Permission.Refused

  def ask(): Unit =
    if (supported() && Build.VERSION.SDK_INT >= 33) Permissions.ask(notifications)(_ => permission @= read())
    else permission @= read()

  private val taps = mutable.LinkedHashMap.empty[String, () => Unit]
  private var shown = 0

  def show(notice: Notice, onClick: () => Unit): Unit =
    if (supported() && read() == Permission.Granted && !(notice.onlyWhenHidden && inFront())) {
      shown += 1
      val key = if (notice.tag.nonEmpty) "tag:" + notice.tag else "n" + shown
      taps.remove(key)
      taps(key) = onClick
      while (taps.size > 100) taps.remove(taps.head._1)
      val context = Host.activity
      val notify = manager
      if (notify.getNotificationChannel(channel) == null)
        notify.createNotificationChannel(new NotificationChannel(channel, "Notifications", NotificationManager.IMPORTANCE_DEFAULT))
      val launch = Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
        .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("everyui.notice", key)
      val info = context.getApplicationInfo
      val notification = new Notification.Builder(context, channel)
        .setSmallIcon(info.icon)
        .setContentTitle(notice.title)
        .setContentText(notice.body)
        .setStyle(new Notification.BigTextStyle().bigText(notice.body))
        .setAutoCancel(true)
        .setContentIntent(PendingIntent.getActivity(context, key.hashCode, launch,
          PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
        .build()
      try notify.notify(key, 0, notification)
      catch { case t: SecurityException => Platform.log("notification not posted: " + t.getMessage) }
    }

  /** The activity's new intent: a tap on one of the app's notices runs what it does. */
  def tapped(intent: Intent): Unit =
    if (intent != null) Option(intent.getStringExtra("everyui.notice")).flatMap(taps.remove).foreach(f => f())

  private def inFront(): Boolean = {
    val state = new ActivityManager.RunningAppProcessInfo
    ActivityManager.getMyMemoryState(state)
    state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
  }
}
