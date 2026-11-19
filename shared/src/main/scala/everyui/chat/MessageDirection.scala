package everyui.chat

/** Where a message goes or came from, where that is not the conversation itself (`ChatEntry.Message.direction`): a
  * reply its author sent to someone elsewhere, or a message that came from elsewhere. Its bubble says so in a small
  * line over its words, with the other party's `face`, and has an edge and a wash of its own; it is never grouped
  * with its author's other messages, and a screen reader hears "Reply to everyui" or "From NaboTV" before its words. */
enum MessageDirection {
  /** Sent to `label` (another session, another conversation): "To everyui". */
  case To(label: String, face: Participant)
  /** Come from `label`: "From NaboTV". */
  case From(label: String, face: Participant)
}

object MessageDirection {
  /** The words over the bubble: "To everyui", "From NaboTV". */
  def shown(d: MessageDirection): String = d match {
    case To(label, _) => ChatWords.sentTo(label)
    case From(label, _) => ChatWords.cameFrom(label)
  }

  /** What a screen reader hears before the words: "Reply to everyui", "From NaboTV". */
  def said(d: MessageDirection): String = d match {
    case To(label, _) => ChatWords.replyTo(label)
    case From(label, _) => ChatWords.cameFrom(label)
  }

  /** The other party. */
  def faceOf(d: MessageDirection): Participant = d match {
    case To(_, face) => face
    case From(_, face) => face
  }
}
