package everyui.render

import android.content.{ClipboardManager, Context}

/** The clipboard read (`everyui.clipboard.Clipboard`) from the system's ClipboardManager, at once: the first item's
  * text (a link or an intent as its text), or None where it holds none. Android 10 on lets only the app in front, with
  * the focus, read it; Android 12 on tells the person it was read. */
object ClipboardAccess {
  def readable(): Boolean = true

  def read(done: Option[String] => Unit): Unit = {
    val manager = Host.activity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip = manager.getPrimaryClip
    val text =
      if (clip == null || clip.getItemCount == 0) None
      else Option(clip.getItemAt(0).coerceToText(Host.activity)).map(_.toString).filter(_.nonEmpty)
    done(text)
  }
}
