package everyui.kit

import everyui.component.*
import everyui.render.Platform

/** A button's press that runs work taking a while: a press is ignored while one is already in flight; the spinner
  * (`spin`, the caller's own) shows only if the work outlasts [[AsyncPress.spinAfter]], so a quick one still looks
  * instant; and when the work says it is done the spinner goes, a failure's message is toasted (`Toast`), and the
  * button can be pressed again. */
class AsyncPress(spin: Boolean => Unit) {
  private var busy = false

  /** Run `work`, which calls the [[AsyncPress.Done]] it is given exactly once when it finishes, on the UI thread or
    * not (the answer is taken on it). A press while busy does nothing. */
  def press(work: AsyncPress.Done => Unit): Unit = if (!busy) {
    busy = true
    Platform.after(AsyncPress.spinAfter, () => if (busy) spin(true))
    var settled = false
    work { result =>
      Platform.later(() => if (!settled) {
        settled = true
        busy = false
        spin(false)
        result match {
          case Left(message) if message.nonEmpty => Toast.show(message)
          case _ => ()
        }
      })
    }
  }

  /** Whether work is in flight. */
  def running: Boolean = busy
}

object AsyncPress {
  /** How the work reports back: `Right(())` it succeeded (the caller has already done what follows, closed its
    * dialog, say); `Left(message)` it failed, and `message` is toasted (empty for none). */
  type Done = Either[String, Unit] => Unit

  /** How long a press must run before the spinner shows; under this, it looks instant. */
  val spinAfter: Double = 0.4
}
