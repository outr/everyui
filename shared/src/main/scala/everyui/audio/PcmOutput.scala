package everyui.audio

/** A platform's stream of sound out, under a [[PcmPlayer]]: chunks of 16-bit mono PCM at the rate it was opened at,
  * played back to back as they are written. Each chunk comes with its number, and the output says (on the thread
  * that draws) the number of the last chunk it played out once nothing is left to play. */
trait PcmOutput {
  /** Queues `samples` to play after what is queued already. */
  def write(samples: Array[Short], number: Long): Unit

  /** Silences it at once, dropping everything queued. */
  def flush(): Unit

  /** How loud, from 0.0 (silent) to 1.0 (as recorded). */
  def volume(level: Double): Unit

  /** Lets the platform's output go for good. */
  def close(): Unit
}
