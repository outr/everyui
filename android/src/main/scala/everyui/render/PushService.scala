package everyui.render

import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import everyui.push.PushMessage

/** Where Firebase Cloud Messaging delivers the app's messages and new tokens (`everyui.push.Push`). The app's
  * manifest declares it, with the MESSAGING_EVENT intent filter (CONSUMING.md); it runs whether or not the app is in
  * front, on a thread of its own. */
class PushService extends FirebaseMessagingService {
  override def onNewToken(token: String): Unit = PushAccess.renewed(token)

  /** A data message's "title", "body" and "path"; a notification message (heard here only while the app is in front)
    * has its title and body under "notification" instead. */
  override def onMessageReceived(message: RemoteMessage): Unit = {
    val data = message.getData
    val shown = Option(message.getNotification)
    val text = (key: String, fallback: Option[String]) => Option(data.get(key)).orElse(fallback).getOrElse("")
    PushAccess.arrived(this, PushMessage(text("title", shown.flatMap(n => Option(n.getTitle))),
      text("body", shown.flatMap(n => Option(n.getBody))), Option(data.get("path")).filter(_.nonEmpty)))
  }
}
