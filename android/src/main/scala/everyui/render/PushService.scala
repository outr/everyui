package everyui.render

import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}
import scala.jdk.CollectionConverters.*

/** Where Firebase Cloud Messaging delivers the app's messages and new tokens (`everyui.push.Push`). The app's
  * manifest declares it, with the MESSAGING_EVENT intent filter (CONSUMING.md); it runs whether or not the app is in
  * front, on a thread of its own. */
class PushService extends FirebaseMessagingService {
  override def onNewToken(token: String): Unit = PushAccess.renewed(token)

  /** A data message's "title", "body", "path", "tag", "renotify" and "actions" (its buttons, JSON text), the rest
    * its data (`PushMessage.of`); a notification message (heard here only while the app is in front) has its title,
    * body and tag under "notification" instead. One with "close" takes away what shows with that tag first, in
    * silence, and is nothing more where it has no title and no body. */
  override def onMessageReceived(message: RemoteMessage): Unit = {
    val shown = Option(message.getNotification)
    PushAccess.handle(this, message.getData.asScala.toMap, shown.flatMap(n => Option(n.getTitle)),
      shown.flatMap(n => Option(n.getBody)), shown.flatMap(n => Option(n.getTag)))
  }
}
