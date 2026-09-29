package everyui.push

/** A push notification, as the server sends it: JSON `{"title": "...", "body": "...", "path": "/runs/42"}` on the
  * web (the Push API's payload), the same three keys in an FCM data message on Android. `path` is where in the app a
  * tap on it goes, the app's own path (`ScreenManager.go`); None stays where the app is. */
case class PushMessage(title: String, body: String, path: Option[String])
