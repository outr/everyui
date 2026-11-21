package everyui.chat

/** An answer the person can give a message with one press (`ChatEntry.Message.replies`): a chip under the bubble
  * saying `label`, which runs `action` when pressed. Chips of the same `group` answer the same question: a message
  * asking several shows each group under its own caption (the group's words), in the order the groups first appear. */
case class QuickReply(label: String, action: () => Unit, group: Option[String] = None)

object QuickReply {
  /** What a screen reader hears of `reply`: "Reply: Yes", or "Reply to Shall I start?: Yes" in a group. */
  def said(reply: QuickReply): String = reply.group match {
    case Some(g) => ChatWords.quickReplyTo(g, reply.label)
    case None => ChatWords.quickReply(reply.label)
  }
}
