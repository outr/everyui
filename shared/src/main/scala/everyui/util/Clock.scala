package everyui.util

import reactify.{Channel, Var}

/** The time, to the second, as a reactive value: what an unfinished span is measured against, so a binding
  * ticks. Advanced by the frame loop (ScreenManager). */
object Clock {
  val now: Var[Long] = Var(System.currentTimeMillis())
  /** Once a second. */
  val tick: Channel[Long] = Channel[Long]
  private var elapsed = 0.0

  def update(delta: Double): Unit = {
    elapsed += delta
    if (elapsed >= 1.0) {
      elapsed = 0.0
      now @= System.currentTimeMillis()
      tick @= now()
    }
  }
}
