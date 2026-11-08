package everyui.background

/** How long work that answered Retry waits before it runs again: `seconds` the first time, then that again each time
  * (linear) or twice as long each time (exponential). Held between ten seconds and five hours, the platform's
  * limits. */
case class WorkBackoff(exponential: Boolean = true, seconds: Double = 30.0) {
  /** The first wait in milliseconds, within the limits. */
  def millis: Long = (math.max(WorkBackoff.least, math.min(WorkBackoff.most, seconds)) * 1000.0).toLong
}

object WorkBackoff {
  /** The shortest first wait, in seconds. */
  val least: Double = 10.0
  /** The longest, in seconds. */
  val most: Double = 18000.0
}
