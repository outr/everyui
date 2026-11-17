package everyui.render

import android.app.{Notification, NotificationChannel, NotificationManager}
import android.content.Context
import android.os.Build
import everyui.push.NoticeImportance

/** A notification built by whichever means this Android has. Channels are Android 8's: before it a builder has none,
  * and the importance a channel would have is the notification's own priority. Android 7 says whether the person lets
  * the app notify them; before it there is nothing to ask, and it may. Called unguarded on Android 6 and 7, the newer
  * calls failed the first notice, push or progress shown. */
private[render] object NoticeChannels {
  /** Whether the person lets the app notify them, as far as this Android says. */
  def enabled(manager: NotificationManager): Boolean = Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled

  /** `channel` made where it is not yet, `importance` a NotificationManager.IMPORTANCE_ (Android 8 and later). */
  def ensure(manager: NotificationManager, channel: String, name: String, importance: Int): Unit =
    if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channel) == null)
      manager.createNotificationChannel(new NotificationChannel(channel, name, importance))

  /** A channel's importance as Android's NotificationManager.IMPORTANCE_: Urgent is High, the highest a channel is made
    * at (Android leaves IMPORTANCE_MAX unused). */
  def level(importance: NoticeImportance): Int = importance match {
    case NoticeImportance.Min => NotificationManager.IMPORTANCE_MIN
    case NoticeImportance.Low => NotificationManager.IMPORTANCE_LOW
    case NoticeImportance.Default => NotificationManager.IMPORTANCE_DEFAULT
    case NoticeImportance.High | NoticeImportance.Urgent => NotificationManager.IMPORTANCE_HIGH
  }

  /** A builder on `channel`, or before Android 8 one at the priority `importance` stands for. */
  def builder(context: Context, channel: String, importance: Int): Notification.Builder =
    if (Build.VERSION.SDK_INT >= 26) new Notification.Builder(context, channel)
    else new Notification.Builder(context).setPriority(priority(importance))

  /** A builder on `channel`, or before Android 8 one at the priority `importance` stands for: Urgent the highest. */
  def builder(context: Context, channel: String, importance: NoticeImportance): Notification.Builder =
    builder(context, channel,
      if (importance == NoticeImportance.Urgent) NotificationManager.IMPORTANCE_MAX else level(importance))

  /** What a channel's importance is as a notification's priority, before Android 8. */
  private def priority(importance: Int): Int =
    if (importance <= NotificationManager.IMPORTANCE_MIN) Notification.PRIORITY_MIN
    else if (importance == NotificationManager.IMPORTANCE_LOW) Notification.PRIORITY_LOW
    else if (importance == NotificationManager.IMPORTANCE_DEFAULT) Notification.PRIORITY_DEFAULT
    else if (importance == NotificationManager.IMPORTANCE_HIGH) Notification.PRIORITY_HIGH
    else Notification.PRIORITY_MAX
}
