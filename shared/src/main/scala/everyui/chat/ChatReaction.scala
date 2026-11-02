package everyui.chat

/** A reaction on a message (`ChatEntry.Message.reactions`): the emoji (or the `:name:` of one of the app's own,
  * `everyui.emoji.CustomEmoji`, drawn as its picture), how many reacted with it, whether the person
  * using the app is among them (`mine`, drawn lit), and who they are, for its tip. The app's store keeps them; a
  * transcript only shows them and says what was pressed (`Transcript.onReact`). */
case class ChatReaction(emoji: String, count: Int, mine: Boolean, who: List[String] = Nil)
