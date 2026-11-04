package everyui.app

/** A window kept for an address not known yet (`Platform.openUrlLater`): reserved while the person's press is still
  * going on, where a browser lets nothing open after it, and sent on once the address comes (`go`), or let go
  * (`cancel`). Either is told once; whatever comes after is nothing.
  *
  * `held` says whether a window really was kept: false where the browser refused even the blank one, so `go` will be
  * refused too, and the app should offer a link the person can press (in whose press it opens). Where nothing refuses
  * a late open, true. */
class UrlTarget(going: String => Unit, cancelling: () => Unit, val held: Boolean = true) {
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
