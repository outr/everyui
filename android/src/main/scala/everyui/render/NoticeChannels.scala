package everyui.render

import android.app.{Notification, NotificationChannel, NotificationManager}
import android.content.Context
import android.os.Build

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

  /** A builder on `channel`, or before Android 8 one at the priority `importance` stands for. */
  def builder(context: Context, channel: String, importance: Int): Notification.Builder =
    if (Build.VERSION.SDK_INT >= 26) new Notification.Builder(context, channel)
    else new Notification.Builder(context).setPriority(priority(importance))

  /** What a channel's importance is as a notification's priority, before Android 8. */
  private def priority(importance: Int): Int =
    if (importance <= NotificationManager.IMPORTANCE_MIN) Notification.PRIORITY_MIN
    else if (importance == NotificationManager.IMPORTANCE_LOW) Notification.PRIORITY_LOW
    else if (importance == NotificationManager.IMPORTANCE_DEFAULT) Notification.PRIORITY_DEFAULT
    else Notification.PRIORITY_HIGH
}
