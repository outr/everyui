package everyui.push

/** What the server needs to push to this app, to send it as it is: `service` "webpush" with `value` the browser's
  * subscription as JSON (`{"endpoint": ..., "expirationTime": ..., "keys": {"p256dh": ..., "auth": ...}}`, what a Web
  * Push library sends to with the app's VAPID key), or "fcm" with `value` the device's FCM registration token. */
case class PushToken(service: String, value: String)
