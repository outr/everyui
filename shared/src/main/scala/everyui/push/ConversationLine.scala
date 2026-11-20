package everyui.push

/** One message of a conversation as Android stacks it (`ConversationStore`): who sent it and their picture's URL ("" for
  * none), its words, when it came (milliseconds since 1970), and whether the person using the device sent it (`me`: a
  * reply typed into the notification). */
case class ConversationLine(sender: String, picture: String, text: String, time: Long, me: Boolean = false) {
  /** It as text, which `ConversationLine.decoded` reads back: kept on the notification, so the conversation is
    * rebuilt from what Android shows when the app's process is new. */
  def encoded: String = List(PushJson.quote(sender), PushJson.quote(picture), PushJson.quote(text), time.toString,
    if (me) "true" else "false").mkString("[", ",", "]")
}

object ConversationLine {
  /** `message`'s line, come at `time`: its sender (`PushConversation.sender`), their picture, and its body (its title
    * where it has none), as plain words. */
  def of(message: PushMessage, time: Long): ConversationLine = {
    val c = message.conversation
    val sender = c.map(_.sender(message.shownTitle)).getOrElse(message.shownTitle)
    ConversationLine(sender, c.flatMap(_.senderPicture).getOrElse(""),
      if (message.body.nonEmpty) message.shownBody else message.shownTitle, time)
  }

  /** A reply the person typed, at `time`. */
  def reply(text: String, time: Long): ConversationLine = ConversationLine("", "", text, time, me = true)

  /** A line `encoded` wrote; None for anything else. */
  def decoded(text: String): Option[ConversationLine] = PushJson.parse(text) match {
    case Some(items: Vector[?]) if items.length == 5 => (items(0), items(1), items(2), items(3), items(4)) match {
      case (sender: String, picture: String, words: String, time: Double, me: Boolean) =>
        Some(ConversationLine(sender, picture, words, time.toLong, me))
      case _ => None
    }
    case _ => None
  }
}
