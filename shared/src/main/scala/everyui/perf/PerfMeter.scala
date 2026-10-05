package everyui.perf

/** What `Perf` measures, kept for the window under way and for the whole session: the key-to-frame times, the app's
  * update in each frame, the time from frame to frame and the frames dropped, and the platform's own frame times where
  * it has them. Times are the platform's ticks (ms); `from` is when it started. */
class PerfMeter(from: Double) {
  private val keys = new PerfHistogram
  private val updates = new PerfHistogram
  private val frames = new PerfHistogram
  private val drawn = new PerfHistogram
  private var dropped = 0
  private var windowFrom = from

  private val sessionKeys = new PerfHistogram
  private val sessionUpdates = new PerfHistogram
  private val sessionFrames = new PerfHistogram
  private var sessionDropped = 0
  private var closed = 0
  private var slow = 0

  /** When the window under way started, and how many have closed, and of them called for less than they ran at. */
  def windowStart: Double = windowFrom
  def windows: Int = closed
  def slowWindows: Int = slow
  def started: Double = from

  /** A key `ms` from its press to the frame drawn after it was handled. */
  def key(ms: Double): Unit = keys.add(ms)

  /** A frame `ms` after the one before, `updateMs` of it the app's update; dropped at `dropMs` or more. */
  def frame(ms: Double, updateMs: Double, dropMs: Double): Unit = {
    frames.add(ms)
    updates.add(updateMs)
    if (ms >= dropMs) dropped += 1
  }

  /** A frame as the platform timed it. */
  def platformFrame(ms: Double): Unit = drawn.add(ms)

  /** The window under way at `now`, as it stands. */
  def window(now: Double, at: Long, memoryLevel: String, memoryUsedPct: Int, level: QualityLevel): PerfWindow =
    PerfWindow(at, (now - windowFrom) / 1000.0, keys.count, keys.percentile(0.5), keys.percentile(0.95),
      frames.count, updates.percentile(0.95), frames.percentile(0.95),
      if (frames.count == 0) -1.0 else dropped.toDouble * 100.0 / frames.count.toDouble,
      drawn.percentile(0.95), memoryLevel, memoryUsedPct, level)

  /** The window under way closed at `now`, counted into the session, and the next begun. */
  def close(now: Double, at: Long, memoryLevel: String, memoryUsedPct: Int, level: QualityLevel,
            policy: QualityPolicy): PerfWindow = {
    val w = window(now, at, memoryLevel, memoryUsedPct, level)
    sessionKeys.addAll(keys)
    sessionUpdates.addAll(updates)
    sessionFrames.addAll(frames)
    sessionDropped += dropped
    closed += 1
    if (policy.wants(w).ordinal > level.ordinal) slow += 1
    keys.clear()
    updates.clear()
    frames.clear()
    drawn.clear()
    dropped = 0
    windowFrom = now
    w
  }

  /** The whole session at `now`, with the window under way: keys, their half and 95th percentile; frames, their
    * update's and their own 95th percentile, and the share dropped. -1 where none were measured. */
  def keyCount: Int = sessionKeys.count + keys.count
  def frameCount: Int = sessionFrames.count + frames.count
  def keyPercentile(share: Double): Double = both(sessionKeys, keys).percentile(share)
  def updatePercentile(share: Double): Double = both(sessionUpdates, updates).percentile(share)
  def framePercentile(share: Double): Double = both(sessionFrames, frames).percentile(share)
  def dropPct: Double = if (frameCount == 0) -1.0 else (sessionDropped + dropped).toDouble * 100.0 / frameCount.toDouble

  private def both(a: PerfHistogram, b: PerfHistogram): PerfHistogram = {
    val h = new PerfHistogram
    h.addAll(a)
    h.addAll(b)
    h
  }
}
