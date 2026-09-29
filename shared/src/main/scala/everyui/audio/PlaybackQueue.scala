package everyui.audio

/** Which chunk a [[PcmPlayer]] is waiting to hear the end of: each chunk queued is numbered, and only the output
  * running dry after the last one queued, with no stop since, is the queue drained. An end from before a stop (a
  * flushed chunk's) or from before a later chunk was queued means nothing. */
final class PlaybackQueue {
  private var numbered: Long = 0L
  private var awaiting: Option[Long] = None

  /** The number of a chunk being queued. */
  def queue(): Long = {
    numbered += 1
    awaiting = Some(numbered)
    numbered
  }

  /** Everything queued dropped: whatever ends after this is not a drain. */
  def stop(): Unit = awaiting = None

  /** The output ran dry after chunk `number`: whether that drains the queue (at most once for each run of chunks). */
  def ended(number: Long): Boolean =
    if (awaiting.contains(number)) {
      awaiting = None
      true
    }
    else false

  /** Whether chunks are queued that have not been heard to the end. */
  def playing: Boolean = awaiting.nonEmpty
}
