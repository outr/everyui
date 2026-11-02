package everyui.render

import android.app.{ActivityManager, Notification, NotificationChannel, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.os.{Build, Handler, Looper}
import everyui.Permission
import everyui.push.{Push, PushAnswer, PushMessage, PushToken}

/** Push notifications (`everyui.push.Push`) through Firebase Cloud Messaging (the app adds `EveryUILibraries.firebase`
  * with its runtime, its Firebase configuration as resources, and `PushService` in its manifest: CONSUMING.md). Where
  * it has not, or on a television, which shows no notifications of this kind, there is no push.
  *
  * A message is a data message, `{"title", "body", "path"}`, which `PushService` hears whether or not the app is in
  * front: in front, it goes to `Push.received`; otherwise it is posted as a notification on a channel of its own
  * ("everyui.push", "Notifications"), whose tap opens the app with the path (`Host.path` for a launch; `tapped`, from
  * the activity's new intent, for an app already running). A notification message (with "notification") the system
  * shows itself when the app is not in front; its data's "path" still reaches the app on a tap. Android 13 and later ask
  * the person before an app posts one (POST_NOTIFICATIONS, which the manifest declares). */
object PushAccess {
  private val channel = "everyui.push"
  // the id every notification with a tag is shown under: its tag tells it from the others
  private val tagged = 0
  private val notifications = "android.permission.POST_NOTIFICATIONS"
  private lazy val main = new Handler(Looper.getMainLooper)

  private lazy val present: Boolean = try Firebase.present() catch { case _: LinkageError => false }

  def supported(): Boolean = present && !Platform.remoteNavigation()

  def listen(): Unit = ()

  def permission(done: Permission => Unit): Unit = done(
    if (!supported()) Permission.Refused
    else if (Build.VERSION.SDK_INT >= 33) Permissions.state(notifications)
    else if (manager(Host.activity).areNotificationsEnabled) Permission.Granted
    else Permission.Refused)

  def permit(done: Boolean => Unit): Unit =
    if (!supported()) done(false)
    else if (Build.VERSION.SDK_INT < 33) done(manager(Host.activity).areNotificationsEnabled)
    else Permissions.ask(notifications)(granted => done(granted && manager(Host.activity).areNotificationsEnabled))

  /** The device's FCM token, once the person lets the app notify them. `key` is the web's; the token's project is the
    * app's Firebase configuration. */
  def subscribe(key: String, done: PushAnswer => Unit): Unit =
    if (!supported()) done(PushAnswer.PushUnsupported)
    else permit { granted =>
      if (!granted) done(PushAnswer.PushDenied)
      else Firebase.token(Host.activity) {
        case Right(token) => done(PushAnswer.Subscribed(PushToken("fcm", token)))
        case Left(why) => done(PushAnswer.PushFailed(why))
      }
    }

  def unsubscribe(done: () => Unit): Unit = if (supported()) Firebase.forget(Host.activity, done) else done()

  /** A token FCM replaced, from `PushService` on a thread of its own. */
  def renewed(token: String): Unit = main.post(() => Push.tokens @= PushToken("fcm", token))

  /** A message, from `PushService` on a thread of its own: the app's while it is in front, else a notification. */
  def arrived(context: Context, message: PushMessage): Unit =
    if (inFront()) main.post(() => Push.received @= message)
    else show(context, message)

  /** The activity's new intent: a tap on one of the app's notifications, or on one the system showed for FCM. */
  def tapped(intent: Intent): Unit =
    if (intent != null && (intent.hasExtra("everyui.push") || intent.hasExtra("google.message_id"))) {
      val text = (names: List[String]) => names.flatMap(n => Option(intent.getStringExtra(n))).headOption.getOrElse("")
      Push.tapped(PushMessage(text(List("everyui.push.title", "gcm.notification.title")),
        text(List("everyui.push.body", "gcm.notification.body")), Option(intent.getStringExtra("path")).filter(_.nonEmpty),
        Option(intent.getStringExtra("everyui.push.tag")).orElse(Option(intent.getStringExtra("tag"))).filter(_.nonEmpty)))
    }

  private def inFront(): Boolean = {
    val state = new ActivityManager.RunningAppProcessInfo
    ActivityManager.getMyMemoryState(state)
    state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
  }

  private def manager(context: Context): NotificationManager = context.getSystemService(classOf[NotificationManager])

  /** The notifications shown with `tag` taken away. */
  def close(tag: String): Unit = if (Host.activity != null) withdraw(Host.activity, tag)

  /** The notifications shown with `tag` taken away: `Push.close`, or a message of `{"close": tag}` (`PushService`). */
  def withdraw(context: Context, tag: String): Unit = manager(context).cancel(tag, tagged)

  private def show(context: Context, message: PushMessage): Unit = {
    val notify = manager(context)
    if (notify.getNotificationChannel(channel) == null)
      notify.createNotificationChannel(new NotificationChannel(channel, "Notifications", NotificationManager.IMPORTANCE_DEFAULT))
    val id = (message.title + message.body + message.path.getOrElse("") + System.nanoTime().toString).hashCode
    val launch = Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
      .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra("everyui.push", true)
      .putExtra("everyui.push.title", message.title)
      .putExtra("everyui.push.body", message.body)
    message.path.foreach(p => launch.putExtra("path", p))
    message.tag.foreach(t => launch.putExtra("everyui.push.tag", t))
    val info = context.getApplicationInfo
    val notification = new Notification.Builder(context, channel)
      .setSmallIcon(info.icon)
      .setContentTitle(if (message.title.nonEmpty) message.title else context.getPackageManager.getApplicationLabel(info).toString)
      .setContentText(message.body)
      .setStyle(new Notification.BigTextStyle().bigText(message.body))
      .setAutoCancel(true)
      // in place of the one shown with its tag, sounding again only where it says so
      .setOnlyAlertOnce(message.tag.nonEmpty && !message.renotify)
      .setContentIntent(PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
      .build()
    try message.tag match {
      case Some(tag) => notify.notify(tag, tagged, notification)
      case None => notify.notify(id, notification)
    }
    catch { case t: SecurityException => android.util.Log.w("everyui", "notification not posted: " + t.getMessage) }
  }
}
