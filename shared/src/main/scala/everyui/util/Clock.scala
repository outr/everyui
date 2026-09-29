package everyui.util

import reactify.{Channel, Var}

/** The time, to the second, as a reactive value: what an unfinished span is measured against, so a binding
  * ticks. Advanced by the frame loop (ScreenManager), which wakes for it once a second while anything follows it
  * (`watched`), and otherwise leaves it be. */
object Clock {
  val now: Var[Long] = Var(System.currentTimeMillis())
  /** Once a second. */
  val tick: Channel[Long] = Channel[Long]

  /** The wall clock, read where it stands now.
    *
    * [[now]] is advanced by the frame loop, so it stands still whenever nothing is drawing — which is exactly the
    * span somebody is away in another app. Measuring how long they were gone against it reads as no time at all.
    * Anything comparing two real moments wants this; anything that must tick a binding wants [[now]]. */
  def real(): Long = System.currentTimeMillis()
  private var elapsed = 0.0

  /** Whether anything follows the clock: a binding that reads [[now]], or a listener on [[tick]]. */
  def watched: Boolean = now.reactions().nonEmpty || tick.reactions().nonEmpty

  /** Seconds until the clock next moves on. */
  def untilTick: Double = math.max(0.0, 1.0 - elapsed)

  def update(delta: Double): Unit = {
    elapsed += delta
    if (elapsed >= 1.0) {
      elapsed = 0.0
      now @= System.currentTimeMillis()
      tick @= now()
    }
  }
}
