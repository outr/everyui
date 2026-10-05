package everyui.perf

/** A stretch of the app's use, as `Perf` measured it (`Perf.windows`, `Perf.current`): `seconds` long, ending at `at`
  * (ms since 1970); the keys pressed in it and how long, in ms, from each to the frame drawn after it was handled (the
  * half and the 95th percentile); the frames the loop ran, the app's own update in them and the time from each to the
  * next (their 95th percentiles), and what share of them were dropped (longer than `QualityPolicy.dropMs`); where
  * the platform times its own frames (Android), theirs; the memory as the platform calls it ("normal", "low",
  * "critical") and the share in use; and the level it ran at. -1, or "", where nothing was measured. */
case class PerfWindow(at: Long,
                      seconds: Double,
                      keys: Int,
                      keyP50: Double,
                      keyP95: Double,
                      frames: Int,
                      updateP95: Double,
                      frameP95: Double,
                      dropPct: Double,
                      platformFrameP95: Double,
                      memoryLevel: String,
                      memoryUsedPct: Int,
                      level: QualityLevel) {
  /** In one line, for a log: `60s keys 12 p50/p95 140/420ms update p95 38ms frame p95 66ms drop 12% of 840 mem normal
    * 41% at Full`. */
  def line: String =
    PerfWindow.whole(seconds) + "s keys " + keys.toString +
      (if (keys > 0) " p50/p95 " + PerfWindow.whole(keyP50) + "/" + PerfWindow.whole(keyP95) + "ms" else "") +
      (if (frames > 0) " update p95 " + PerfWindow.whole(updateP95) + "ms frame p95 " + PerfWindow.whole(frameP95) +
        "ms drop " + PerfWindow.whole(dropPct) + "% of " + frames.toString else " no frames") +
      (if (platformFrameP95 >= 0.0) " drawn p95 " + PerfWindow.whole(platformFrameP95) + "ms" else "") +
      " mem " + (if (memoryLevel.isEmpty) "-" else memoryLevel) +
      (if (memoryUsedPct >= 0) " " + memoryUsedPct.toString + "%" else "") +
      " at " + level.toString
}

object PerfWindow {
  /** Rounded to a whole number, as a log reads it. */
  def whole(d: Double): String = math.round(d).toString
}
