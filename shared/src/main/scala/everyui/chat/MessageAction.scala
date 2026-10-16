package everyui.chat

import everyui.clipboard.Clipboard
import everyui.font.MaterialIcon

/** Something done to a message from the small row of buttons beside it (`Transcript.messageActions`): a glyph, what it
  * does (its tooltip, and what a screen reader says of it) and the doing. `done`, where it is given, is said once it has
  * run: the glyph turns to a tick for a moment with those words as its tooltip, and a screen reader reads them out
  * ("Copied"). */
case class MessageAction(icon: MaterialIcon, label: String, action: () => Unit, done: String = "")

object MessageAction {
  /** Copy message: `m`'s words on the clipboard as they read (`Clipboard.copyMarkdown`, a line break in them a line
    * break, as the transcript shows them): clean HTML where they are pasted with styles (an email, a document), plain
    * prose where they are not, and "Copied" said. A transcript that shows its messages as plain text
    * (`MessageText.plain`) offers `copyMarkdownSource`'s doing under this one's name instead. */
  def copyText(m: ChatEntry.Message): MessageAction =
    MessageAction(MaterialIcon.ContentCopy, ChatWords.copyMessage, () => copy(m), ChatWords.copied)

  /** Copy as Markdown: `m`'s text as it was written, marks and all, and "Copied" said. Not offered by default; an app
    * adds it to `Transcript.messageActions`:
    * {{{
    *   transcript.messageActions @= {
    *     case m: ChatEntry.Message => transcript.actionsFor(m) :+ MessageAction.copyMarkdownSource(m)
    *     case _ => Nil
    *   }
    * }}} */
  def copyMarkdownSource(m: ChatEntry.Message): MessageAction =
    MessageAction(MaterialIcon.Code, ChatWords.copyMarkdown, () => Clipboard.copy(m.text()), ChatWords.copied)

  /** `m`'s words on the clipboard, as Copy message puts them. */
  def copy(m: ChatEntry.Message): Unit = Clipboard.copyMarkdown(m.text(), breaks = true)
}
