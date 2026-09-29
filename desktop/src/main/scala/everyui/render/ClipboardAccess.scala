package everyui.render

/** The clipboard read (`everyui.clipboard.Clipboard`) from JavaFX's system clipboard, at once: its text, or None where
  * it holds none. */
object ClipboardAccess {
  def readable(): Boolean = true

  def read(done: Option[String] => Unit): Unit = {
    val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
    done(if (clipboard.hasString) Option(clipboard.getString).filter(_.nonEmpty) else None)
  }
}
