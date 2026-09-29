package everyui.render

import everyui.Permission
import everyui.push.{Push, PushAnswer, PushMessage, PushToken}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

/** Push notifications (`everyui.push.Push`) through the Push API and the page's service worker, the one
  * `everyui.sbt.WebApp.write` installs (sw.js), which shows a notification that arrives while no window of the app is
  * in sight, opens or focuses the app at its path when it is tapped, and otherwise hands the message to the page. What
  * it hands over comes to the page as a message: `{everyui: "push", message}` (arrived while open), `{everyui: "open",
  * message}` (tapped), `{everyui: "subscription", subscription}` (renewed by the browser). */
object PushAccess {
  private def window: js.Dynamic = dom.window.asInstanceOf[js.Dynamic]
  private def workers: js.Dynamic = dom.window.navigator.asInstanceOf[js.Dynamic].serviceWorker

  def supported(): Boolean = window.isSecureContext.asInstanceOf[Boolean] && !js.isUndefined(workers) &&
    js.typeOf(window.PushManager) == "function" && js.typeOf(window.Notification) == "function"

  /** What the service worker hands the page, heard from the first use of `Push`. */
  def listen(): Unit = if (supported()) workers.addEventListener("message", (e: js.Dynamic) => {
    val data = e.data
    if (data != null && !js.isUndefined(data) && !js.isUndefined(data.everyui)) {
      val kind = String.valueOf(data.everyui)
      if (kind == "push") Push.received @= message(data.message)
      else if (kind == "open") Push.tapped(message(data.message))
      else if (kind == "subscription") Push.tokens @= PushToken("webpush", String.valueOf(data.subscription))
    }
  })

  def permission(done: Permission => Unit): Unit = done(
    if (!supported()) Permission.Refused
    else String.valueOf(window.Notification.permission) match {
      case "granted" => Permission.Granted
      case "denied" => Permission.Refused
      case _ => Permission.NotAsked
    })

  def permit(done: Boolean => Unit): Unit =
    if (!supported()) done(false)
    else window.Notification.requestPermission().asInstanceOf[js.Promise[String]]
      .`then`[Unit]((answer: String) => done(answer == "granted"))
      .`catch`[Unit]((_: Any) => done(false))

  /** The page's service worker's subscription with `key`: the one it has (unless it was made with another key, which
    * is let go first), or a new one, which asks for the permission if it has not been given. */
  def subscribe(key: String, done: PushAnswer => Unit): Unit =
    if (!supported()) done(PushAnswer.PushUnsupported)
    else registration { found =>
      found match {
        case None => done(PushAnswer.PushFailed("No service worker on the page: WebApp.write installs one"))
        case Some(r) =>
          val manager = r.pushManager
          val wanted = Base64Url.decode(key)
          val subscribed = (s: js.Dynamic) => done(PushAnswer.Subscribed(PushToken("webpush", js.JSON.stringify(s.toJSON()))))
          val fresh = () => manager.subscribe(js.Dynamic.literal(userVisibleOnly = true, applicationServerKey = wanted))
            .asInstanceOf[js.Promise[js.Dynamic]]
          manager.getSubscription().asInstanceOf[js.Promise[js.Dynamic]]
            .`then`[js.Dynamic] { (existing: js.Dynamic) =>
              if (existing == null) fresh()
              else if (sameKey(existing, wanted)) js.Promise.resolve[js.Dynamic](existing)
              else existing.unsubscribe().asInstanceOf[js.Promise[Any]].`then`[js.Dynamic]((_: Any) => fresh())
            }
            .`then`[Unit]((s: js.Dynamic) => subscribed(s))
            .`catch`[Unit] { (e: Any) =>
              val refused = WebErrors.name(e) == "NotAllowedError" || String.valueOf(window.Notification.permission) == "denied"
              done(if (refused) PushAnswer.PushDenied else PushAnswer.PushFailed(WebErrors.message(e)))
            }
      }
    }

  def unsubscribe(done: () => Unit): Unit =
    if (!supported()) done()
    else registration {
      case None => done()
      case Some(r) => r.pushManager.getSubscription().asInstanceOf[js.Promise[js.Dynamic]]
        .`then`[Any]((s: js.Dynamic) => if (s == null) js.Promise.resolve[Any](()) else s.unsubscribe().asInstanceOf[js.Promise[Any]])
        .`then`[Unit]((_: Any) => done())
        .`catch`[Unit]((_: Any) => done())
    }

  /** The page's service worker registration once it is running, or None where the page registered none. */
  private def registration(found: Option[js.Dynamic] => Unit): Unit =
    workers.getRegistration().asInstanceOf[js.Promise[js.UndefOr[js.Dynamic]]]
      .`then`[Unit] { (r: js.UndefOr[js.Dynamic]) =>
        if (r.isEmpty) found(None)
        else workers.ready.asInstanceOf[js.Promise[js.Dynamic]].`then`[Unit]((ready: js.Dynamic) => found(Some(ready)))
      }
      .`catch`[Unit]((_: Any) => found(None))

  private def sameKey(subscription: js.Dynamic, key: Uint8Array): Boolean = {
    val had = subscription.options.applicationServerKey
    had != null && !js.isUndefined(had) && {
      val bytes = new Uint8Array(had.asInstanceOf[scala.scalajs.js.typedarray.ArrayBuffer])
      bytes.length == key.length && (0 until key.length).forall(i => bytes(i) == key(i))
    }
  }

  private def message(m: js.Dynamic): PushMessage = {
    val text = (v: js.Dynamic) => if (v == null || js.isUndefined(v)) "" else String.valueOf(v)
    val path = text(m.path)
    PushMessage(text(m.title), text(m.body), if (path.isEmpty) None else Some(path))
  }
}
