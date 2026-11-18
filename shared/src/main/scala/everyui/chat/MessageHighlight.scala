package everyui.chat

import everyui.markdown.{MarkdownPlain, Passage}
import everyui.theme.Tone

/** A passage of a message to highlight where it is (`ChatEntry.Message.highlights`): a question to the person in the
  * middle of a long reply, say. `text` is its words as they are written in the message's Markdown, found where they
  * first are whatever the spaces and line breaks between them and with or without the marks around them (`Passage`);
  * a passage that is not there is passed over, and one in code is never marked. It is drawn on a wash of `tone` with a
  * line of it under, and a screen reader hears it after the message, with `label` ("Question for you: Shall I start
  * with group 1?"), or "Highlighted" where it has none.
  *
  * {{{
  *   reply.highlights @= Vector(MessageHighlight("Shall I start with group 1?", label = ChatWords.questionForYou))
  * }}} */
case class MessageHighlight(text: String, tone: Tone = Tone.Warning, label: String = "") {
  /** It as a Markdown view marks it. */
  def passage: Passage = Passage(text, tone)

  /** What a screen reader hears of it: its label, or "Highlighted", then its words as they read. */
  def said: String = (if (label.isEmpty) ChatWords.highlighted else label) + ": " + MarkdownPlain.spoken(text)
}
