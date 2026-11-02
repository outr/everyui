package everyui.push

/** A push notification, as the server sends it: JSON `{"title": "...", "body": "...", "path": "/runs/42"}` on the
  * web (the Push API's payload), the same keys in an FCM data message on Android. `path` is where in the app a tap on
  * it goes, the app's own path (`ScreenManager.go`); None stays where the app is.
  *
  * `tag` groups it with the others of the same tag (a conversation's): shown, it takes the place of the one with that
  * tag the system shows, rather than another beside it, and `Push.close` closes it. `renotify` (`"renotify": true`)
  * has such a replacement sound or buzz again; without it, a replacement changes what shows quietly.
  *
  * A push of `{"close": "tag"}` alone takes away what shows with that tag, the person having read it elsewhere: on
  * Android at once, in silence. Not to a browser: one that shows nothing for a push is punished for it (Chrome shows a
  * notice of its own, Safari ends the subscription after a few), so there the server sends none, and the page closes
  * its own as they are read (`Push.close`). */
case class PushMessage(title: String, body: String, path: Option[String], tag: Option[String] = None,
                       renotify: Boolean = false)
