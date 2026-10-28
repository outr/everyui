package everyui.chat

/** A reaction pressed on a message (`Transcript.onReact`): the message's id, the emoji, and whether the person using
  * the app is adding it (`on`) or taking theirs away. The app updates the message's `reactions` from its store. */
case class Reacted(entryId: String, emoji: String, on: Boolean)
