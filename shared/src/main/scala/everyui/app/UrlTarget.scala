package everyui.app

/** A window kept for an address not known yet (`Platform.openUrlLater`): reserved while the person's press is still
  * going on, where a browser lets nothing open after it, and sent on once the address comes (`go`), or let go
  * (`cancel`). Either is told once; whatever comes after is nothing. */
class UrlTarget(going: String => Unit, cancelling: () => Unit) {
  private var done = false

  /** The window sent to `url`. */
  def go(url: String): Unit = if (!done) {
    done = true
    going(url)
  }

  /** The window let go, closed where one was opened: the address will not come. */
  def cancel(): Unit = if (!done) {
    done = true
    cancelling()
  }
}
