package everyui.render

import android.app.{ActivityManager, Notification, NotificationChannel, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.graphics.drawable.Icon
import android.os.{Build, Bundle, Handler, Looper}
import everyui.Permission
import everyui.activity.ServiceRuntime
import everyui.background.Background
import everyui.push.{Push, PushAction, PushAnswer, PushButton, PushMessage, PushToken}
import scala.jdk.CollectionConverters.*

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
  /** A notification's button that runs in the background, as `BackgroundReceiver` hears it. */
  val actionIntent = "everyui.push.ACTION"
  private val actionExtra = "everyui.push.action"
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
    if (intent != null && intent.hasExtra(actionExtra)) {
      dismiss(Host.activity, intent)
      Push.chose(actionOf(intent), go = true)
    }
    // one of the app's own: the push whole, its buttons and data with it
    else if (intent != null && intent.hasExtra("everyui.push") && intent.hasExtra("everyui.push.actions"))
      Push.tapped(actionOf(intent).message)
    else if (intent != null && (intent.hasExtra("everyui.push") || intent.hasExtra("google.message_id"))) {
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
    val launch = put(launcher(context), message, None)
    message.path.foreach(p => launch.putExtra("path", p))
    val info = context.getApplicationInfo
    val builder = new Notification.Builder(context, channel)
      .setSmallIcon(NoticeIcon.of(context))
      .setContentTitle(if (message.title.nonEmpty) message.title else context.getPackageManager.getApplicationLabel(info).toString)
      .setContentText(message.body)
      .setStyle(new Notification.BigTextStyle().bigText(message.body))
      .setAutoCancel(true)
      // in place of the one shown with its tag, sounding again only where it says so
      .setOnlyAlertOnce(message.tag.nonEmpty && !message.renotify)
      .setContentIntent(PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
    // its buttons: one that runs in the background a broadcast to the BackgroundReceiver where the app has a
    // BackgroundApp, else (and one that opens the app) the app's launch with the button chosen
    val shownAs = (message.tag.getOrElse(""), if (message.tag.nonEmpty) tagged else id)
    message.actions.take(PushButton.most).zipWithIndex.foreach { (b, i) =>
      val code = (shownAs._2.toString + "#" + message.tag.getOrElse("") + "#" + i.toString).hashCode
      val flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
      val chosen =
        if (b.background && Background.supported)
          PendingIntent.getBroadcast(context, code, chose(put(new Intent(context, classOf[BackgroundReceiver])
            .setAction(actionIntent), message, Some(b.id)), shownAs), flags)
        else {
          val opening = chose(put(launcher(context), message, Some(b.id)), shownAs)
          b.path.orElse(message.path).foreach(p => opening.putExtra("path", p))
          PendingIntent.getActivity(context, code, opening, flags)
        }
      builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, NoticeIcon.of(context)),
        b.label, chosen).build())
    }
    val notification = builder.build()
    try message.tag match {
      case Some(tag) => notify.notify(tag, tagged, notification)
      case None => notify.notify(id, notification)
    }
    catch { case t: SecurityException => android.util.Log.w("everyui", "notification not posted: " + t.getMessage) }
  }

  private def launcher(context: Context): Intent =
    Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
      .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)

  /** `intent` carrying `message` whole (and the button chosen, where one was), for `actionOf` to read back. */
  private def put(intent: Intent, message: PushMessage, button: Option[String]): Intent = {
    val data = new Bundle
    message.data.foreach((k, v) => data.putString(k, v))
    intent.putExtra("everyui.push", true)
      .putExtra("everyui.push.title", message.title)
      .putExtra("everyui.push.body", message.body)
      .putExtra("everyui.push.renotify", message.renotify)
      .putExtra("everyui.push.actions", PushButton.json(message.actions))
      .putExtra("everyui.push.data", data)
    message.path.foreach(p => intent.putExtra("everyui.push.path", p))
    message.tag.foreach(t => intent.putExtra("everyui.push.tag", t))
    button.foreach(b => intent.putExtra(actionExtra, b))
    intent
  }

  // which notification it was shown as, to take it away once a button on it is chosen
  private def chose(intent: Intent, shownAs: (String, Int)): Intent =
    intent.putExtra("everyui.push.shownTag", shownAs._1).putExtra("everyui.push.shownId", shownAs._2)

  /** The button chosen and its push, from an intent `put` wrote. */
  def actionOf(intent: Intent): PushAction = {
    val text = (k: String) => Option(intent.getStringExtra(k)).getOrElse("")
    val data = Option(intent.getBundleExtra("everyui.push.data")).map(b =>
      b.keySet.asScala.toList.flatMap(k => Option(b.getString(k)).map(k -> _)).toMap)
      .getOrElse(Map.empty[String, String])
    PushAction(text(actionExtra), PushMessage(text("everyui.push.title"), text("everyui.push.body"),
      Some(text("everyui.push.path")).filter(_.nonEmpty), Some(text("everyui.push.tag")).filter(_.nonEmpty),
      intent.getBooleanExtra("everyui.push.renotify", false), PushButton.parse(text("everyui.push.actions")), data))
  }

  /** The notification a button was chosen on taken away: Android leaves it showing. */
  def dismiss(context: Context, intent: Intent): Unit =
    if (context != null && intent.hasExtra("everyui.push.shownId")) {
      val tag = Option(intent.getStringExtra("everyui.push.shownTag")).getOrElse("")
      val id = intent.getIntExtra("everyui.push.shownId", 0)
      try { if (tag.nonEmpty) manager(context).cancel(tag, id) else manager(context).cancel(id) }
      catch { case _: Throwable => () }
    }

  /** The activity started by a button that opens the app: `Push.acted` hears it once the app's `main` has run (it is
    * already at the path). */
  def launched(intent: Intent): Unit = if (intent != null && intent.hasExtra(actionExtra)) {
    dismiss(Host.activity, intent)
    val action = actionOf(intent)
    main.post(() => Push.chose(action, go = false))
  }

  /** A data message as `PushService` hears it, with what a notification message says where the data does not: a close
    * first, then the push, unless a close is all it is. */
  def handle(context: Context, data: Map[String, String], title: Option[String], body: Option[String],
             tag: Option[String]): Unit = {
    ServiceRuntime.init(context)
    val closing = data.getOrElse("close", "")
    if (closing.nonEmpty) withdraw(context, closing)
    val sent = PushMessage.of(data)
    val pushed = sent.copy(title = if (sent.title.nonEmpty) sent.title else title.getOrElse(""),
      body = if (sent.body.nonEmpty) sent.body else body.getOrElse(""),
      tag = sent.tag.orElse(tag.filter(_.nonEmpty)))
    if (closing.isEmpty || pushed.title.nonEmpty || pushed.body.nonEmpty) arrived(context, pushed)
  }

  /** `data` handled as `PushService` handles a data message (`Push.deliver`). */
  def deliver(data: Map[String, String]): Unit = handle(ServiceRuntime.context, data, None, None, None)
}
