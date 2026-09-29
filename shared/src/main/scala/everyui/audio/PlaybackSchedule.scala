package everyui.audio

/** When each chunk starts on an output's clock (in seconds), so that chunks play back to back with no gap: a chunk
  * starts where the one before ends, or, when the output has already run dry, `lead` seconds from now (time to get
  * it there). The web schedules its buffers by this. */
final class PlaybackSchedule(val lead: Double = 0.03) {
  private var end: Double = 0.0

  /** Where a chunk lasting `seconds`, queued at `now`, starts. */
  def start(now: Double, seconds: Double): Double = {
    val at = if (end > now) end else now + lead
    end = at + seconds
    at
  }

  /** How much is queued past `now`, in seconds. */
  def ahead(now: Double): Double = if (end > now) end - now else 0.0

  /** Nothing queued any more (after a stop). */
  def reset(): Unit = end = 0.0
}
