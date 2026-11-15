package everyui.push

/** What becomes of a push notification once a reply typed into it is handled (`BackgroundApp.onPushReply`). Android
  * shows a spinner on the notification until it is posted again or taken away, so it is always one or the other. Its
  * cases' names are their own, since a Roku channel's type names are global. */
sealed trait ReplyOutcome

object ReplyOutcome {
  /** Posted again as it was, quietly, with the reply under it (Android's reply history): the conventional answer, and
    * the default. */
  case object ReplyShown extends ReplyOutcome
  /** Taken away, the conversation answered. */
  case object ReplyClosed extends ReplyOutcome
  /** Posted quietly in its place as `message` ("Sent", or the conversation with the reply in it), whatever its tag. */
  case class ReplyUpdated(message: PushMessage) extends ReplyOutcome
}
