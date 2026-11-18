package everyui.push

import everyui.Permission
import everyui.app.screen.ScreenManager
import everyui.async.Task
import everyui.background.Background
import everyui.render.{Platform, PushAccess}
import reactify.*

/** Push notifications: the person's permission, a subscription (or token) for the server to push to, and what arrives.
  * A notification that arrives while the app is open is the app's (`received`), to show its own way, and the system
  * shows nothing; one that arrives while it is not is shown by the system, and a tap on it opens the app at the
  * message's path (`opened`, then `ScreenManager.go`).
  *
  * The web subscribes with the Push API through the service worker `WebApp.write` installs (sw.js), with the app's
  * VAPID public key; Android asks Firebase Cloud Messaging for a token, with the app's Firebase configuration
  * (CONSUMING.md). The desktop, a Roku and an Apple TV have no push (`supported` false).
  *
  * {{{
  *   Push.received.attach(m => Toast.show(m.title + ": " + m.body))
  *   Push.tokens.attach(server.registerPush)
  *   Push.subscribe(vapidPublicKey).map {
  *     case PushAnswer.Subscribed(token) => server.registerPush(token)
  *     case other => scribe.info(other.toString)
  *   }.start()
  * }}} */
object Push {
  /** A notification that arrived while the app was open: the system showed nothing, so the app shows it. */
  val received: Channel[PushMessage] = Channel[PushMessage]

  /** A data push (`PushMessage.isData`: neither title nor body, or `"dataOnly": "true"`) that arrived while the app
    * was in front, its keys as sent (`PushMessage.dataOf`): the system shows nothing, and the app does what it asks (a
    * sync). One that arrives while the app is not in front goes to its `BackgroundApp.onPush` instead, on Android
    * (once either way); where there is none, here. The web hands it to an open window of the app, in sight or not. */
  val data: Channel[Map[String, String]] = Channel[Map[String, String]]

  /** A notification the person tapped while the app was running, before the app goes to its path (if it has one).
    * One tapped while the app was closed opens the app at its path instead, which the app's screens answer as a link. */
  val opened: Channel[PushMessage] = Channel[PushMessage]

  /** A button on a notification chosen (`PushButton`): its id and the push. One that opens the app is heard here
    * before the app goes to its path, with the app running or as it starts. One that runs in the background is heard
    * here while the app's screens run, and by the app's `BackgroundApp.onPushAction` while they do not (on
    * Android; a browser opens the app for it where no window of it is open): once either way. A reply typed into one
    * (`PushButton.input`) goes to `BackgroundApp.onPushReply` on Android, app open or closed, and is heard here on
    * the web, with its words in `PushAction.text` where the browser took any. One of either kind that requires an unlock
    * (`PushButton.requiresUnlock`) opens the app before Android 12, and is heard here as one that opens it is. A copy
    * button (`PushButton.copyText`) is heard neither here nor by `onPushAction` unless it is a background button too. */
  val acted: Channel[PushAction] = Channel[PushAction]

  /** A new token for the server, where the platform replaced the one it had (a refreshed FCM token, a Web Push
    * subscription the browser renewed). Send it as a `Subscribed` token is sent. */
  val tokens: Channel[PushToken] = Channel[PushToken]

  /** Whether this platform has push notifications for the app. */
  def supported: Boolean = PushAccess.supported()

  /** Whether the person lets the app show notifications. */
  def permission: Task[Permission] = Task.async[Permission](done => PushAccess.permission(done))

  /** Ask for it (the platform's own prompt, where there is one), and whether it was given. The web asks only from a
    * click; best asked once the person knows what the notifications are for. */
  def permit(): Task[Boolean] = Task.async[Boolean](done => PushAccess.permit(done))

  /** A subscription for the server: asks for the permission first where it is not given. `key` is the app's VAPID
    * public key (base64url), which the web subscribes with; Android's token comes from the app's Firebase project
    * instead. Subscribing again answers the same subscription while it lasts, so an app sends it on every start. */
  def subscribe(key: String): Task[PushAnswer] = Task.async[PushAnswer](done => PushAccess.subscribe(key, done))

  /** No more pushes to this app: the subscription (or token) let go. */
  def unsubscribe(): Task[Unit] = Task.async[Unit](done => PushAccess.unsubscribe(() => done(())))

  /** The notifications the system shows on this device with `tag` (`PushMessage.tag`) taken away: the conversation
    * they are for read here. Nothing where there are none, or no push. */
  def close(tag: String): Unit = if (tag.nonEmpty) PushAccess.close(tag)

  /** The app's channels made now where they are not yet (Android 8 and later), so the settings list them, for the
    * person to tune, before the first push of each kind: each at its importance, which Android keeps from then on (the
    * person's to change, never the app's); a channel already made has its name, description and group brought up to
    * date. Called as the app starts (or from its `BackgroundApp`), with every channel its server posts into. Nothing
    * where there are no channels: the web, the desktop, a Roku, an Apple TV, and Android before 8.
    *
    * {{{
    *   Push.channels(List(
    *     PushChannel("calls", "Calls", "talk", "Talk", NoticeImportance.High),
    *     PushChannel("messages", "Messages", "talk", "Talk"),
    *     PushChannel("digest", "Daily digest", importance = NoticeImportance.Low)))
    * }}} */
  def channels(list: List[PushChannel]): Unit = PushAccess.declare(list)

  /** The settings page of the channel `id` opened, for the person to give it its sound or importance (an app's
    * "Notification sound..."), and whether it was: answered as they come back to the app. Android 8 and later open the
    * channel's own page (the app's notifications where the channel is not made yet); before 8, the app's
    * notifications or, where the device lacks that page, the app's details. False where there are no such pages (the
    * web, the desktop, a Roku, an Apple TV), or no window to open one from. */
  def openChannelSettings(id: String): Task[Boolean] =
    Task.async[Boolean](done => PushAccess.channelSettings(id, done))

  /** A data push arrived, its keys as sent: heard by `data` where the app's screens are in front (`front`) or no
    * `BackgroundApp` is made ready in the process, else by its `onPush`, whose task the platform holds the process
    * for. */
  private[everyui] def dataArrived(sent: Map[String, String], front: Boolean): Task[Unit] = {
    val keys = PushMessage.dataOf(sent)
    val said = "push: data push (" + keys.keys.toList.sorted.mkString(", ") + ") to "
    Background.app match {
      case Some(app) if !front =>
        Platform.log(said + "onPush")
        app.onPush(keys)
      case _ =>
        Platform.log(said + "Push.data")
        data @= keys
        Task.unit
    }
  }

  /** A notification tapped: `opened` hears it, then the running screens go to its path. */
  private[everyui] def tapped(message: PushMessage): Unit = {
    opened @= message
    message.path.foreach(p => ScreenManager.running.foreach(_.go(p)))
  }

  /** A button chosen: `acted` hears it, then, where it opens the app (or `opened` it, a background button that waited
    * for the unlock: `PushButton.opensToUnlock`) and `go`, the running screens go to its path. */
  private[everyui] def chose(action: PushAction, go: Boolean, opened: Boolean = false): Unit = {
    acted @= action
    if (go && (opened || !action.background)) action.path.foreach(p => ScreenManager.running.foreach(_.go(p)))
  }

  /** A push handled as if the platform's push service had delivered `data` (an FCM data message's keys): for the
    * example's demo and checks on a device with no push service. Nothing where the platform has no push. */
  private[everyui] def deliver(data: Map[String, String]): Unit = PushAccess.deliver(data)

  PushAccess.listen()
}
