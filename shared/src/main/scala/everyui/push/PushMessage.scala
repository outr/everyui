package everyui.push

/** A push notification, as the server sends it: JSON `{"title": "...", "body": "...", "path": "/runs/42"}` on the
  * web (the Push API's payload), the same keys in an FCM data message on Android. `path` is where in the app a tap on
  * it goes, the app's own path (`ScreenManager.go`); None stays where the app is.
  *
  * `tag` groups it with the others of the same tag (a conversation's): shown, it takes the place of the one with that
  * tag the system shows, rather than another beside it, and `Push.close` closes it. `renotify` (`"renotify": true`)
  * has such a replacement sound or buzz again; without it, a replacement changes what shows quietly.
  *
  * A push of `{"close": "tag"}` alone takes away what shows with that tag, the person having read it elsewhere, and
  * shows nothing: on Android at once, in silence, and in a browser through the app's service worker, with no window of
  * the app open. A browser holds a push that shows nothing against the site: Chrome lets one pass now and then and
  * otherwise shows a notice of its own, Firefox allows a few and then ends the subscription, Safari ends it after a
  * few. So a server sends a browser one only where it sent a notification with that tag that is not yet closed, and
  * none to Safari's (an endpoint on `push.apple.com`), where the page closes its own as they are read (`Push.close`);
  * CONSUMING.md has the rest.
  *
  * `actions` are its buttons (`PushButton`, JSON text under "actions"), which the person may choose instead of
  * tapping it (`Push.acted`); `data` is the rest of what the server sent with it, by key. */
case class PushMessage(title: String, body: String, path: Option[String], tag: Option[String] = None,
                       renotify: Boolean = false, actions: Vector[PushButton] = Vector.empty,
                       data: Map[String, String] = Map.empty)

object PushMessage {
  /** The keys a push's data says what it is with; the rest are its `data`. */
  val keys: Set[String] = Set("title", "body", "path", "tag", "renotify", "close", "actions")

  /** A push from its data as the server sent it, every value text (an FCM data message's): its title, body, path,
    * tag, renotify ("true"), its buttons (`PushButton.parse` of "actions"), and the rest as `data`, less what the
    * push service adds itself (keys from "google." and "gcm."). */
  def of(data: Map[String, String]): PushMessage = {
    val text = (k: String) => data.getOrElse(k, "")
    PushMessage(text("title"), text("body"), Some(text("path")).filter(_.nonEmpty),
      Some(text("tag")).filter(_.nonEmpty), text("renotify") == "true", PushButton.parse(text("actions")),
      data.filter((k, _) => !keys.contains(k) && !k.startsWith("google.") && !k.startsWith("gcm.")))
  }
}
