package everyui.chat

/** Whether the conversation is reaching whatever it talks to: shown over the composer only while it is not. */
sealed abstract class ConnectionState

object ConnectionState {
  case object Connected extends ConnectionState
  /** On its way there, or back. */
  case object Connecting extends ConnectionState
  /** Not there: `reason` says why where the app knows, and `retry`, where the app can try again at once, is offered as a
    * control that calls it. */
  final case class Offline(reason: Option[String] = None, retry: Option[() => Unit] = None) extends ConnectionState
}
