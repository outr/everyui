package everyui.kit

import everyui.component.*
import everyui.render.Platform

/** A button's press that runs work taking a while: a press is ignored while one is already in flight; the spinner
  * (`spin`, the caller's own) shows only if the work outlasts [[AsyncPress.spinAfter]], so a quick one still looks
  * instant, and once shown stays at least `minSpin` seconds, so work ending just after it shows does not flash it for a
  * frame; and when the work says it is done its result applies at once (a failure's message is toasted, `Toast`), the
  * button can be pressed again, and the spinner goes once it has had its time. A press while it is still up keeps it
  * spinning for the new work. */
class AsyncPress(spin: Boolean => Unit, minSpin: Double = AsyncPress.minSpin) {
  private var busy = false
  // when the spinner showed (ms), or -1 while it is not up; and the press under way, so a timer of one before is
  // ignored
  private var shownAt = -1L
  private var presses = 0

  /** Run `work`, which calls the [[AsyncPress.Done]] it is given exactly once when it finishes, on the UI thread or
    * not (the answer is taken on it). A press while busy does nothing. */
  def press(work: AsyncPress.Done => Unit): Unit = if (!busy) {
    busy = true
    presses += 1
    val at = presses
    if (shownAt < 0L) AsyncPress.after(AsyncPress.spinAfter, () => if (busy && presses == at && shownAt < 0L) {
      shownAt = AsyncPress.clock()
      spin(true)
    })
    var settled = false
    work { result =>
      Platform.later(() => if (!settled) {
        settled = true
        busy = false
        settle(at)
        result match {
          case Left(message) if message.nonEmpty => Toast.show(message)
          case _ => ()
        }
      })
    }
  }

  // the spinner taken down once it has been up `minSpin`, unless another press has it by then
  private def settle(at: Int): Unit = if (shownAt >= 0L) {
    val left = minSpin - (AsyncPress.clock() - shownAt) / 1000.0
    if (left <= 0.0) {
      shownAt = -1L
      spin(false)
    }
    else AsyncPress.after(left, () => if (!busy && presses == at && shownAt >= 0L) {
      shownAt = -1L
      spin(false)
    })
  }

  /** Whether work is in flight. */
  def running: Boolean = busy

  /** Whether the spinner is up: while work runs past `spinAfter`, and for what is left of `minSpin` after. */
  def spinning: Boolean = shownAt >= 0L
}

object AsyncPress {
  /** How the work reports back: `Right(())` it succeeded (the caller has already done what follows, closed its
    * dialog, say); `Left(message)` it failed, and `message` is toasted (empty for none). */
  type Done = Either[String, Unit] => Unit

  /** How long a press must run before the spinner shows; under this, it looks instant. */
  val spinAfter: Double = 0.4

  /** How long the spinner stays once shown, at the least (Material's half second): work ending just after it shows
    * would otherwise flash it for a frame. */
  val minSpin: Double = 0.5

  /** The clock (ms) and the timer the presses keep: the platform's, or a spec's own, moved on as it says. */
  private[everyui] var clock: () => Long = () => System.currentTimeMillis()
  private[everyui] var after: (Double, () => Unit) => Unit = (seconds, f) => Platform.after(seconds, f)
}
