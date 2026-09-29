package everyui.chat

/** A character that starts a word the [[Composer]] offers completions for: '@' for a mention anywhere, '/' for a
  * command only at the start of the message (`atStart`). */
case class CompletionTrigger(char: Char, atStart: Boolean = false)
