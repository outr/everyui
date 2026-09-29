package everyui.render

/** The clipboard read (`everyui.clipboard.Clipboard`): a remote copies nothing and there is no clipboard for an app to
  * read, so a read answers None at once. */
object ClipboardAccess {
  def readable(): Boolean = false

  def read(done: Option[String] => Unit): Unit = done(None)
}
