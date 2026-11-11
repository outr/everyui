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
    * prose where they are not, and "Copied" said. The words are what `text` gives for `m` as it is pressed, so a
    * streaming message copies as far as it has come: its text by default, a transcript's `Transcript.copiedText` where
    * the app copies its messages as other words than it draws. A transcript that shows its messages as plain text
    * (`MessageText.plain`) offers `copyMarkdownSource`'s doing under this one's name instead. */
  def copyText(m: ChatEntry.Message, text: ChatEntry.Message => String = _.text()): MessageAction =
    MessageAction(MaterialIcon.ContentCopy, ChatWords.copyMessage, () => copy(m, text), ChatWords.copied)

  /** Copy as Markdown: what `text` gives for `m` as it is pressed (its text as it was written, marks and all, by
    * default) on the clipboard as it is, and "Copied" said. Not offered by default; an app adds it to
    * `Transcript.messageActions`, with the transcript's `copiedText` where it gives one:
    * {{{
    *   transcript.messageActions @= {
    *     case m: ChatEntry.Message =>
    *       transcript.actionsFor(m) :+ MessageAction.copyMarkdownSource(m, transcript.copiedText)
    *     case _ => Nil
    *   }
    * }}} */
  def copyMarkdownSource(m: ChatEntry.Message, text: ChatEntry.Message => String = _.text()): MessageAction =
    MessageAction(MaterialIcon.Code, ChatWords.copyMarkdown, () => Clipboard.copy(text(m)), ChatWords.copied)

  /** What `text` gives for `m` (its text by default) on the clipboard, as Copy message puts it. */
  def copy(m: ChatEntry.Message, text: ChatEntry.Message => String = _.text()): Unit =
    Clipboard.copyMarkdown(text(m), breaks = true)
}
