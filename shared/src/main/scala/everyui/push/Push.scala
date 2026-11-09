package everyui.push

import everyui.Permission
import everyui.app.screen.ScreenManager
import everyui.async.Task
import everyui.render.PushAccess
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

  /** A notification the person tapped while the app was running, before the app goes to its path (if it has one).
    * One tapped while the app was closed opens the app at its path instead, which the app's screens answer as a link. */
  val opened: Channel[PushMessage] = Channel[PushMessage]

  /** A button on a notification chosen (`PushButton`): its id and the push. One that opens the app is heard here
    * before the app goes to its path, with the app running or as it starts. One that runs in the background is heard
    * here while the app's screens run, and by the app's `BackgroundApp.onPushAction` while they do not (on
    * Android; a browser opens the app for it where no window of it is open): once either way. */
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

  /** A notification tapped: `opened` hears it, then the running screens go to its path. */
  private[everyui] def tapped(message: PushMessage): Unit = {
    opened @= message
    message.path.foreach(p => ScreenManager.running.foreach(_.go(p)))
  }

  /** A button chosen: `acted` hears it, then, where it opens the app and `go`, the running screens go to its path. */
  private[everyui] def chose(action: PushAction, go: Boolean): Unit = {
    acted @= action
    if (go && !action.background) action.path.foreach(p => ScreenManager.running.foreach(_.go(p)))
  }

  /** A push handled as if the platform's push service had delivered `data` (an FCM data message's keys): for the
    * example's demo and checks on a device with no push service. Nothing where the platform has no push. */
  private[everyui] def deliver(data: Map[String, String]): Unit = PushAccess.deliver(data)

  PushAccess.listen()
}
