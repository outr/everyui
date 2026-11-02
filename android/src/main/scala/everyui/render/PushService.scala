package everyui.render

import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import everyui.push.PushMessage

/** Where Firebase Cloud Messaging delivers the app's messages and new tokens (`everyui.push.Push`). The app's
  * manifest declares it, with the MESSAGING_EVENT intent filter (CONSUMING.md); it runs whether or not the app is in
  * front, on a thread of its own. */
class PushService extends FirebaseMessagingService {
  override def onNewToken(token: String): Unit = PushAccess.renewed(token)

  /** A data message's "title", "body", "path", "tag" and "renotify"; a notification message (heard here only while
    * the app is in front) has its title, body and tag under "notification" instead. One with "close" takes away what
    * shows with that tag first, in silence, and is nothing more where it has no title and no body. */
  override def onMessageReceived(message: RemoteMessage): Unit = {
    val data = message.getData
    val shown = Option(message.getNotification)
    val text = (key: String, fallback: Option[String]) => Option(data.get(key)).orElse(fallback).getOrElse("")
    val closing = text("close", None)
    if (closing.nonEmpty) PushAccess.withdraw(this, closing)
    val pushed = PushMessage(text("title", shown.flatMap(n => Option(n.getTitle))),
      text("body", shown.flatMap(n => Option(n.getBody))), Option(data.get("path")).filter(_.nonEmpty),
      Option(text("tag", shown.flatMap(n => Option(n.getTag)))).filter(_.nonEmpty), text("renotify", None) == "true")
    if (closing.isEmpty || pushed.title.nonEmpty || pushed.body.nonEmpty) PushAccess.arrived(this, pushed)
  }
}
