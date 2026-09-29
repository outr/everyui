package everyui.clipboard

import everyui.async.Task
import everyui.render.{ClipboardAccess, Platform}

/** The system's clipboard, for text: copied to it where the platform has one (`copies`), and read back where it lets an
  * app read it (`readable`). The web copies and reads through `navigator.clipboard`, the desktop through JavaFX's
  * clipboard, Android through its ClipboardManager. A Roku and an Apple TV have no clipboard to speak of.
  *
  * {{{
  *   if (Clipboard.readable) paste.event.click.attach(_ => Clipboard.read().map(_.foreach(field.value @= _)).start())
  * }}} */
object Clipboard {
  /** Whether `copy` reaches a clipboard. */
  def copies: Boolean = Platform.clipboard()

  /** `text` on the clipboard, in place of what was there. */
  def copy(text: String): Unit = Platform.copyText(text)

  /** Whether an app can read the clipboard here. A browser may still ask the person first, or refuse. */
  def readable: Boolean = ClipboardAccess.readable()

  /** The clipboard's text, or None where it holds none, or the platform (or the person) would not let the app read it.
    * A browser reads it only from a click or a key, and may ask the person first; best asked for from a Paste
    * button. */
  def read(): Task[Option[String]] = Task.async[Option[String]](done => ClipboardAccess.read(done))
}
