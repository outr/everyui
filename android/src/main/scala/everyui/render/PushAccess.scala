package everyui.render

import android.app.{ActivityManager, Notification, NotificationChannel, NotificationChannelGroup, NotificationManager,
  PendingIntent, RemoteInput}
import android.content.{Context, Intent}
import android.graphics.drawable.Icon
import android.os.{Build, Bundle, Handler, Looper}
import everyui.Permission
import everyui.activity.ServiceRuntime
import everyui.background.Background
import everyui.push.{Push, PushAction, PushAnswer, PushButton, PushChannel, PushFormat, PushMessage,
  PushToken, ReplyOutcome}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*

/** Push notifications (`everyui.push.Push`) through Firebase Cloud Messaging (the app adds `EveryUILibraries.firebase`
  * with its runtime, its Firebase configuration as resources, and `PushService` in its manifest: CONSUMING.md). Where
  * it has not, or on a television, which shows no notifications of this kind, there is no push.
  *
  * A message is a data message, `{"title", "body", "path"}`, which `PushService` hears whether or not the app is in
  * front: in front, it goes to `Push.received`; otherwise it is posted as a notification on the channel it names
  * (`PushChannel`, made or renamed as it is named) or EveryUI's own ("everyui.push", "Notifications"), stacked under its
  * group's summary where it has a group, whose tap opens the app with the path (`Host.path` for a launch; `tapped`, from
  * the activity's new intent, for an app already running). A notification message (with "notification") the system
  * shows itself when the app is not in front; its data's "path" still reaches the app on a tap. One with neither title
  * nor body, or with "dataOnly" "true", is a data push (`PushMessage.isData`), never posted: `Push.data` hears it in
  * front, the app's `BackgroundApp.onPush` otherwise, in a process started for it where the app was closed. Android
  * 13 and later ask the person before an app posts one (POST_NOTIFICATIONS, which the manifest declares). A posted
  * one has the small icon its "noticeIcon" names (`NoticeIcon`), the colour of its "noticeColor" and the picture of
  * its "noticePicture" (`NoticePicture`), where it names them. */
object PushAccess {
  /** A notification's button that runs in the background, as `BackgroundReceiver` hears it. */
  val actionIntent = "everyui.push.ACTION"
  private val actionExtra = "everyui.push.action"
  private val messageExtra = "everyui.push.message"
  // the words of a reply, as RemoteInput hands them over
  private val replyKey = "everyui.push.reply"
  // a background button opening the app to be unlocked (PushButton.opensToUnlock), going to its path as it opens
  private val unlockExtra = "everyui.push.unlock"
  // the id every notification with a tag is shown under: its tag tells it from the others
  private val tagged = 0
  private val notifications = "android.permission.POST_NOTIFICATIONS"
  private lazy val main = new Handler(Looper.getMainLooper)
  // the channels a push asked another importance of, said once each
  private val differing = new java.util.concurrent.ConcurrentHashMap[String, String]

  private lazy val present: Boolean = try Firebase.present() catch { case _: LinkageError => false }

  def supported(): Boolean = present && !Platform.remoteNavigation()

  def listen(): Unit = ()

  def permission(done: Permission => Unit): Unit = done(
    if (!supported()) Permission.Refused
    else if (Build.VERSION.SDK_INT >= 33) Permissions.state(notifications)
    else if (NoticeChannels.enabled(manager(Host.activity))) Permission.Granted
    else Permission.Refused)

  def permit(done: Boolean => Unit): Unit =
    if (!supported()) done(false)
    else if (Build.VERSION.SDK_INT < 33) done(NoticeChannels.enabled(manager(Host.activity)))
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
      val action = actionOf(intent)
      if (opening(Host.activity, intent, action))
        Push.chose(action, go = true, opened = intent.getBooleanExtra(unlockExtra, false))
    }
    // one of the app's own: the push whole, its buttons and data with it
    else if (intent != null && intent.hasExtra(messageExtra)) Push.tapped(actionOf(intent).message)
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

  /** `message` posted: as `at` (its tag and id) where it is posted again in its own place, else under its shown tag or
    * an id of its own; quietly where it is posted again, with `replies` under it (Android's reply history). Off the
    * main thread where its picture is still to fetch (`NoticePicture`). */
  private def show(context: Context, message: PushMessage, at: Option[(String, Int)] = None,
                   replies: Vector[String] = Vector.empty): Unit =
    if (message.fetchedPicture.forall(u => NoticePicture.ready(context, u))) post(context, message, at, replies)
    else NoticePicture.later("push: notification not posted", () => post(context, message, at, replies))

  private def post(context: Context, message: PushMessage, at: Option[(String, Int)],
                   replies: Vector[String]): Unit = {
    val notify = manager(context)
    val channel = message.channel.getOrElse(PushChannel.default)
    open(notify, channel)
    val id = (message.title + message.body + message.path.getOrElse("") + System.nanoTime().toString).hashCode
    val shownAs = at.getOrElse(message.shownTag.map(t => (t, tagged)).getOrElse(("", id)))
    val launch = put(launcher(context), message, None)
    message.path.foreach(p => launch.putExtra("path", p))
    val info = context.getApplicationInfo
    // a Markdown body styled (the collapsed view its first line), its title and lines plain words: PushSpans
    val body = PushSpans.body(message)
    val title = if (message.title.nonEmpty) message.shownTitle else context.getPackageManager.getApplicationLabel(info).toString
    val builder = NoticeChannels.builder(context, channel.id, channel.importance)
      .setSmallIcon(NoticeIcon.small(context, message.shownIcon))
      .setContentTitle(title)
      .setContentText(if (message.format == PushFormat.Markdown) PushSpans.firstLine(body) else body)
      .setStyle(new Notification.BigTextStyle().bigText(body))
      .setAutoCancel(true)
      // in place of the one shown with its tag, sounding again only where it says so
      .setOnlyAlertOnce(at.nonEmpty || (message.shownTag.nonEmpty && !message.renotify))
      .setContentIntent(PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
    message.noticeColor.foreach(c =>
      builder.setColor((c.alphaInt << 24) | (c.redInt << 16) | (c.greenInt << 8) | c.blueInt))
    message.fetchedPicture.flatMap(u => NoticePicture.of(context, u)).foreach(b => builder.setLargeIcon(b))
    message.group.foreach(g => builder.setGroup(g))
    if (message.summary && message.group.nonEmpty) {
      builder.setGroupSummary(true)
      if (message.lines.nonEmpty) {
        val inbox = new Notification.InboxStyle().setBigContentTitle(title)
        message.shownLines.foreach(l => inbox.addLine(l))
        if (message.body.nonEmpty) inbox.setSummaryText(message.shownBody)
        builder.setStyle(inbox)
      }
      // the summary never sounds: the newest of the group does as it comes
      if (Build.VERSION.SDK_INT >= 26) builder.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
    }
    // silenced as NotificationCompat silences one: a group's child alerting only through a summary it never has
    else if (message.silent && Build.VERSION.SDK_INT >= 26) {
      if (message.group.isEmpty) builder.setGroup("everyui.silent")
      builder.setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
    }
    if (replies.nonEmpty && Build.VERSION.SDK_INT >= 24) builder.setRemoteInputHistory(replies.toArray[CharSequence])
    // its buttons: one that runs in the background, takes a reply or copies, a broadcast to the BackgroundReceiver
    // where the app has a BackgroundApp, else (and one that opens the app) the app's launch with the button chosen;
    // one that requires an unlock waits for it from Android 12, and before opens the app, which the lock screen guards.
    // (A copy button's text stays off a locked screen that hides what notifications say: Android shows a private
    // notification there as the app's name alone, with no buttons.)
    message.actions.take(PushButton.most).zipWithIndex.foreach { (b, i) =>
      val code = (shownAs._2.toString + "#" + shownAs._1 + "#" + i.toString).hashCode
      val flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
      val unlocking = b.opensToUnlock(Build.VERSION.SDK_INT)
      val copies = b.copyText.nonEmpty
      val takes = b.input && !copies && !unlocking && Background.supported && Build.VERSION.SDK_INT >= 24
      val chosen =
        if (takes || ((b.background || copies) && !unlocking && Background.supported))
          PendingIntent.getBroadcast(context, code, chose(put(new Intent(context, classOf[BackgroundReceiver])
            .setAction(actionIntent), message, Some(b.id)), shownAs), if (takes) mutable else flags)
        else {
          val opening = chose(put(launcher(context), message, Some(b.id)), shownAs)
          if (unlocking) opening.putExtra(unlockExtra, true)
          // a copy alone opens the app where it was, copying as it opens
          if (!copies || b.background) b.path.orElse(message.path).foreach(p => opening.putExtra("path", p))
          PendingIntent.getActivity(context, code, opening, flags)
        }
      val action = new Notification.Action.Builder(Icon.createWithResource(context, NoticeIcon.of(context)), b.label, chosen)
      if (takes) action.addRemoteInput(new RemoteInput.Builder(replyKey)
        .setLabel(if (b.hint.nonEmpty) b.hint else b.label).build())
      // the unlock asked for before it runs (Android 12 on: PushButton.unlockSdk)
      if (b.requiresUnlock && Build.VERSION.SDK_INT >= 31) action.setAuthenticationRequired(true)
      builder.addAction(action.build())
    }
    val notification = builder.build()
    try {
      if (shownAs._1.nonEmpty) notify.notify(shownAs._1, shownAs._2, notification) else notify.notify(shownAs._2, notification)
    }
    catch { case t: SecurityException => android.util.Log.w("everyui", "notification not posted: " + t.getMessage) }
  }

  /** A copy button's text put on the clipboard (`PushCopy`), and its notification taken away where it says so
    * (`copyDismisses`); false, and nothing done, for any other button. */
  def copied(context: Context, intent: Intent, action: PushAction): Boolean = action.button.flatMap(_.copyText) match {
    case Some(text) =>
      PushCopy(context, text)
      if (action.button.exists(_.copyDismisses)) dismiss(context, intent)
      true
    case None => false
  }

  /** A button that opened the app, as the activity hears it: a copy button's text copied (where the app has no
    * BackgroundApp to copy it closed, or it waited for the unlock), any other's notification taken away; whether the
    * app hears it (`Push.acted`), as it does all but a copy alone. */
  private def opening(context: Context, intent: Intent, action: PushAction): Boolean =
    if (copied(context, intent, action)) action.background
    else {
      dismiss(context, intent)
      true
    }

  /** A reply's intent: the system writes the words into it, so mutable, which Android 12 must be told. */
  private def mutable: Int =
    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
    else PendingIntent.FLAG_UPDATE_CURRENT

  /** `channel` made at its importance where it is not yet, or its name, description (or group) brought up to date
    * (Android 8 and later). Android keeps the importance it was made with, the person's to change and never the app's,
    * so one that has another than `channel` asks for is left as it is, and said once (at debug). */
  private def open(notify: NotificationManager, channel: PushChannel): Unit = if (Build.VERSION.SDK_INT >= 26) {
    val had = notify.getNotificationChannel(channel.id)
    val group = Option(channel.group).filter(_.nonEmpty)
    val level = NoticeChannels.level(channel.importance)
    if (had != null && had.getImportance != level && differing.putIfAbsent(channel.id, channel.id) == null)
      android.util.Log.d("everyui", "push: channel " + channel.id + " asked " + channel.importance.word + " (" +
        level.toString + "), has importance " + had.getImportance.toString + ", as made or as the person set it: kept")
    if (had == null || had.getName.toString != channel.name || group.exists(g => had.getGroup != g) ||
      (channel.description.nonEmpty && had.getDescription != channel.description)) {
      group.foreach(g => notify.createNotificationChannelGroup(new NotificationChannelGroup(g,
        if (channel.groupName.nonEmpty) channel.groupName else g)))
      // brought up to date at the importance it has: a lower one would lower it while the person has not touched it
      val made = new NotificationChannel(channel.id, channel.name, if (had == null) level else had.getImportance)
      val description = if (channel.description.nonEmpty || had == null) channel.description else had.getDescription
      if (description != null && description.nonEmpty) made.setDescription(description)
      group.orElse(Option(had).flatMap(h => Option(h.getGroup))).foreach(g => made.setGroup(g))
      notify.createNotificationChannel(made)
    }
  }

  /** The app's channels made, or brought up to date, before a push names them (`Push.channels`). */
  def declare(channels: List[PushChannel]): Unit = if (Build.VERSION.SDK_INT >= 26) {
    try {
      val notify = manager(ServiceRuntime.context)
      channels.foreach(c => open(notify, c))
    }
    catch { case t: Throwable => Platform.logError("push: channels not declared", t) }
  }

  /** The settings page of the channel `id` (`Push.openChannelSettings`), the app's notifications where it is not made
    * yet or before Android 8. */
  def channelSettings(id: String, done: Boolean => Unit): Unit = {
    val made = Build.VERSION.SDK_INT >= 26 && Host.activity != null &&
      manager(Host.activity).getNotificationChannel(id) != null
    SettingsPages.channel(id, made, done)
  }

  private def launcher(context: Context): Intent =
    Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
      .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)

  /** `intent` carrying `message` whole, as the keys it was sent with (and the button chosen, where one was), for
    * `actionOf` to read back. */
  private def put(intent: Intent, message: PushMessage, button: Option[String]): Intent = {
    val fields = new Bundle
    message.fields.foreach((k, v) => fields.putString(k, v))
    intent.putExtra("everyui.push", true).putExtra(messageExtra, fields)
    button.foreach(b => intent.putExtra(actionExtra, b))
    intent
  }

  // which notification it was shown as, to take it away once a button on it is chosen
  private def chose(intent: Intent, shownAs: (String, Int)): Intent =
    intent.putExtra("everyui.push.shownTag", shownAs._1).putExtra("everyui.push.shownId", shownAs._2)

  /** The button chosen and its push, from an intent `put` wrote, with the words of a reply typed into it. */
  def actionOf(intent: Intent): PushAction = {
    val fields = Option(intent.getBundleExtra(messageExtra)).map(b =>
      b.keySet.asScala.toList.flatMap(k => Option(b.getString(k)).map(k -> _)).toMap)
      .getOrElse(Map.empty[String, String])
    val reply = Option(RemoteInput.getResultsFromIntent(intent)).flatMap(r => Option(r.getCharSequence(replyKey)))
    PushAction(Option(intent.getStringExtra(actionExtra)).getOrElse(""), PushMessage.of(fields), reply.map(_.toString))
  }

  /** The notification a button was chosen on taken away: Android leaves it showing. */
  def dismiss(context: Context, intent: Intent): Unit =
    if (context != null && intent.hasExtra("everyui.push.shownId")) {
      val tag = Option(intent.getStringExtra("everyui.push.shownTag")).getOrElse("")
      val id = intent.getIntExtra("everyui.push.shownId", 0)
      try { if (tag.nonEmpty) manager(context).cancel(tag, id) else manager(context).cancel(id) }
      catch { case _: Throwable => () }
    }

  /** A reply handled (`BackgroundApp.onPushReply`): the notification it was typed into posted again in its place, with
    * the reply or as another, or taken away, as `outcome` says; Android spins on it until one or the other. */
  def replied(context: Context, intent: Intent, action: PushAction, outcome: ReplyOutcome): Unit = {
    val at = (Option(intent.getStringExtra("everyui.push.shownTag")).getOrElse(""), intent.getIntExtra("everyui.push.shownId", 0))
    try outcome match {
      case ReplyOutcome.ReplyClosed => dismiss(context, intent)
      case ReplyOutcome.ReplyShown => show(context, action.message, Some(at), action.text.toVector)
      case ReplyOutcome.ReplyUpdated(message) => show(context, message, Some(at))
    }
    catch { case t: Throwable => Platform.logError("push: the notification replied to not updated", t) }
  }

  /** The activity started by a button that opens the app: `Push.acted` hears it once the app's `main` has run (it is
    * already at the path). */
  def launched(intent: Intent): Unit = if (intent != null && intent.hasExtra(actionExtra)) {
    val action = actionOf(intent)
    if (opening(Host.activity, intent, action)) main.post(() => Push.chose(action, go = false))
  }

  /** A message as `PushService` hears it, with what a notification message says where the data does not: a close
    * first, then a data push (`PushMessage.isData`) to the app, or a notification, unless a close is all it is.
    * `held` (`PushService`, on a thread of its own) waits for a data push's handler to answer, or for its deadline,
    * as Firebase holds the process only while `onMessageReceived` runs. */
  def handle(context: Context, data: Map[String, String], title: Option[String], body: Option[String],
             tag: Option[String], held: Boolean): Unit = {
    ServiceRuntime.init(context)
    val closing = data.getOrElse("close", "")
    if (closing.nonEmpty) withdraw(context, closing)
    val shown = data ++ List("title" -> title, "body" -> body)
      .collect { case (k, Some(v)) if v.nonEmpty && data.getOrElse(k, "").isEmpty => k -> v }
    if (PushMessage.isData(shown)) dataArrived(context, shown, held)
    else {
      val sent = PushMessage.of(shown)
      val pushed = sent.copy(tag = sent.tag.orElse(tag.filter(_.nonEmpty)))
      if (pushed.title.nonEmpty || pushed.body.nonEmpty) arrived(context, pushed)
    }
  }

  /** A data push: `Push.data` while the app is in front, else the app's `BackgroundApp.onPush`, made ready in this
    * process where it is not (a process started for the message), held to the receiver's deadline. Nothing is shown. */
  private def dataArrived(context: Context, sent: Map[String, String], held: Boolean): Unit = {
    val front = inFront()
    if (!front && Background.supported) BackgroundAccess.start(context)
    val heard = new CountDownLatch(1)
    BackgroundAccess.run("push data", Push.dataArrived(sent, front), BackgroundAccess.receiverDeadline, ())(_ =>
      heard.countDown())
    if (held) heard.await(BackgroundAccess.receiverDeadline + 1000L, TimeUnit.MILLISECONDS)
  }

  /** `data` handled as `PushService` handles a data message (`Push.deliver`), on the main thread, so not held. */
  def deliver(data: Map[String, String]): Unit = handle(ServiceRuntime.context, data, None, None, None, held = false)
}
