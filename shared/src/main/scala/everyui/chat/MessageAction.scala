package everyui.chat

import everyui.clipboard.Clipboard
import everyui.font.MaterialIcon

/** Something done to a message from the small row of buttons beside it (`Transcript.messageActions`): a glyph, what it
  * does (its tooltip, and what a screen reader says of it) and the doing. `done`, where it is given, is said once it has
  * run: the glyph turns to a tick for a moment with those words as its tooltip, and a screen reader reads them out
  * ("Copied"). */
case class MessageAction(icon: MaterialIcon, label: String, action: () => Unit, done: String = "")

object MessageAction {
  /** Copy message: `m`'s text, as it was written, on the clipboard, and "Copied" said. */
  def copyText(m: ChatEntry.Message): MessageAction =
    MessageAction(MaterialIcon.ContentCopy, ChatWords.copyMessage, () => Clipboard.copy(m.text()), ChatWords.copied)
}
