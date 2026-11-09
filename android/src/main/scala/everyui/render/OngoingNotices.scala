package everyui.render

import android.app.{Notification, NotificationChannel, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import everyui.activity.ServiceRuntime
import everyui.push.{NoticeImportance, NoticeTap, OngoingNotice}

/** Ongoing notices (`everyui.push.Ongoing`) on Android: posted from the application's context, so a `BackgroundApp`'s
  * handler with no activity shows, updates and clears them too. Each is ongoing (not swiped away before Android 14),
  * alerts once (an update makes no sound), and is on the app's own channel. A tap that runs an action is an explicit
  * broadcast to `BackgroundReceiver`; one that opens the app is the app's launch with the path, as a widget's is, so
  * no receiver starts an activity (Android 12 forbids it from a notice). The small icon is the drawable
  * `everyui_notice` where the app's resources have one, the app's icon otherwise. Not on a television, which shows no
  * notices of this kind; Android 13 and later need the person's leave (POST_NOTIFICATIONS). */
object OngoingNotices {
  private val notifications = "android.permission.POST_NOTIFICATIONS"
  private val id = 0x4f4e

  private def tag(key: String): String = "everyui.ongoing:" + key

  def supported(): Boolean = try {
    !ServiceRuntime.context.getPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
  }
  catch { case _: IllegalStateException => false }

  private def manager(context: Context): NotificationManager = context.getSystemService(classOf[NotificationManager])

  private def allowed(context: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(notifications) == PackageManager.PERMISSION_GRANTED) &&
      manager(context).areNotificationsEnabled

  def show(notice: OngoingNotice): Boolean = try {
    val context = ServiceRuntime.context
    if (!allowed(context)) {
      Platform.log("ongoing: " + notice.key + " not shown: the person has not allowed notices")
      false
    }
    else {
      val notify = manager(context)
      val c = notice.channel
      val channel = new NotificationChannel(c.key, c.name, c.importance match {
        case NoticeImportance.Min => NotificationManager.IMPORTANCE_MIN
        case NoticeImportance.Low => NotificationManager.IMPORTANCE_LOW
        case NoticeImportance.Default => NotificationManager.IMPORTANCE_DEFAULT
      })
      if (c.description.nonEmpty) channel.setDescription(c.description)
      channel.setShowBadge(false)
      // made, or its name and description brought up to date: Android keeps the importance it was made with
      notify.createNotificationChannel(channel)
      val icon = NoticeIcon.of(context)
      val builder = new Notification.Builder(context, c.key)
        .setSmallIcon(icon)
        .setContentTitle(notice.title)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setCategory(Notification.CATEGORY_STATUS)
        .setDeleteIntent(broadcast(context, BackgroundAccess.dismissedIntent, notice.key, "", Map.empty,
          code(notice.key, -1)))
      if (notice.body.nonEmpty)
        builder.setContentText(notice.body).setStyle(new Notification.BigTextStyle().bigText(notice.body))
      notice.tap.foreach(t => builder.setContentIntent(pending(context, notice.key, t, code(notice.key, 0))))
      notice.shownActions.zipWithIndex.foreach { (a, i) =>
        builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, icon), a.label,
          pending(context, notice.key, a.tap, code(notice.key, i + 1))).build())
      }
      notify.notify(tag(notice.key), id, builder.build())
      Platform.log("ongoing: " + notice.key + " shown: " + notice.title)
      true
    }
  }
  catch {
    case t: Throwable =>
      Platform.logError("ongoing: " + notice.key + " not shown", t)
      false
  }

  def clear(key: String): Unit = try {
    manager(ServiceRuntime.context).cancel(tag(key), id)
    Platform.log("ongoing: " + key + " cleared")
  }
  catch { case t: Throwable => Platform.logError("ongoing: " + key + " not cleared", t) }

  // each of a notice's intents its own request code: Android tells PendingIntents apart by it, not by their extras
  private def code(key: String, part: Int): Int = (key + "#" + part.toString).hashCode

  private def pending(context: Context, key: String, tap: NoticeTap, code: Int): PendingIntent = tap match {
    case NoticeTap.RunAction(name, args) => broadcast(context, BackgroundAccess.noticeIntent, key, name, args, code)
    case NoticeTap.OpenApp(path) =>
      val launch = Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
        .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("path", path)
        .putExtra(BackgroundAccess.openedExtra, true)
      PendingIntent.getActivity(context, code, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
  }

  private def broadcast(context: Context, action: String, key: String, name: String, args: Map[String, String],
                        code: Int): PendingIntent = {
    val intent = new Intent(context, classOf[BackgroundReceiver]).setAction(action)
      .putExtra(BackgroundAccess.noticeExtra, key)
      .putExtra(BackgroundAccess.actionExtra, name)
      .putExtra(BackgroundAccess.argsExtra, BackgroundAccess.bundle(args))
    PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
  }
}
