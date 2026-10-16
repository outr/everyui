package everyui.clipboard

import everyui.async.Task
import everyui.markdown.{Markdown, MarkdownHtml, MarkdownPlain}
import everyui.render.{ClipboardAccess, Platform}

/** The system's clipboard, for text, and for text with its HTML (`copyRich`): copied to it where the platform has one
  * (`copies`), and read back where it lets an app read it (`readable`). The web copies and reads through
  * `navigator.clipboard`, the desktop through JavaFX's clipboard, Android through its ClipboardManager. A Roku and an Apple TV have no clipboard to speak of.
  *
  * {{{
  *   if (Clipboard.readable) paste.event.click.attach(_ => Clipboard.read().map(_.foreach(field.value @= _)).start())
  * }}} */
object Clipboard {
  /** Whether `copy` reaches a clipboard. */
  def copies: Boolean = Platform.clipboard()

  /** `text` on the clipboard, in place of what was there. */
  def copy(text: String): Unit = Platform.copyText(text)

  /** `html` and `text` on the clipboard together, in place of what was there: what is pasted where there are styles
    * (an email, a document) is the HTML, and where there are none (a terminal, a plain field) the text. Where the
    * platform's clipboard holds only text (a browser that will not write HTML), `text` alone. */
  def copyRich(html: String, text: String): Unit = Platform.copyRich(html, text)

  /** `markdown` on the clipboard as clean HTML (`MarkdownHtml`: paragraphs, lists, bold, links, tables, with no look
    * of their own) and as readable plain prose (`MarkdownPlain`), so an agent's formatted answer pastes into an email
    * as its words and not as its marks. With `breaks`, a line break in the text is a line break, as a chat's messages
    * have it. */
  def copyMarkdown(markdown: String, breaks: Boolean = false): Unit = {
    val blocks = Markdown.parse(markdown)
    copyRich(MarkdownHtml.blocks(blocks, breaks), MarkdownPlain.blocks(blocks, breaks))
  }

  /** The words selected on the page as the HTML and the text they copy as, where they lie in views that say how
    * (`Component.copyAs`: a Markdown view's paragraphs, lists and tables), as a Copy key copies them; else None, as
    * where nothing is selected or the platform selects no text. A context menu's Copy, asked as the menu opens, copies
    * the same as the key. */
  def selection(): Option[(String, String)] = Platform.selectedRich()

  /** Whether an app can read the clipboard here. A browser may still ask the person first, or refuse. */
  def readable: Boolean = ClipboardAccess.readable()

  /** The clipboard's text, or None where it holds none, or the platform (or the person) would not let the app read it.
    * A browser reads it only from a click or a key, and may ask the person first; best asked for from a Paste
    * button. */
  def read(): Task[Option[String]] = Task.async[Option[String]](done => ClipboardAccess.read(done))
}
