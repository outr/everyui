package everyui.render

import everyui.tvos.uiClipboardGet

/** The clipboard read (`everyui.clipboard.Clipboard`): on an iPhone or an iPad the general pasteboard's text, at once,
  * or None where it holds none (iOS asks the person first where another app put it there, and None comes where they
  * refuse). An Apple TV has no pasteboard: a remote copies nothing, so a read answers None at once. */
object ClipboardAccess {
  def readable(): Boolean = !Platform.tv()

  def read(done: Option[String] => Unit): Unit =
    if (Platform.tv()) done(None)
    else {
      val text = uiClipboardGet()
      done(if (text.isEmpty) None else Some(text))
    }
}
