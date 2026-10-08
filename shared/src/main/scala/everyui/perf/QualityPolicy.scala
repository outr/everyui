package everyui.perf

/** When a session steps itself down a level (`Quality.policy`), and how it is measured: every threshold in one place.
  * The defaults are placeholders, set to agree with NaboTV's server, until a slow device's own numbers (a Roku TV
  * against a Roku Ultra) say where they belong.
  *
  * The app is measured in windows of `windowSeconds`. A window calls for Reduced when its 95th percentile key-to-frame
  * time is `reducedKeyMs` or more (over at least `minKeys` keys), `reducedDropPct` of its frames or more were dropped
  * (over at least `minFrames`), or the platform says its memory is low; for Minimal, the same past `minimalKeyMs`,
  * `minimalDropPct`, or memory critical. `slowWindows` such windows in a row, each calling for less than the session
  * runs at, step it down one level: one bad minute does not.
  * It never steps back up, and the next launch starts from the level it is given (`Quality.start`).
  *
  * A frame is dropped that missed at least one vsync of the display's own refresh: it came `dropVsyncs` (one and a
  * half) of its refresh periods or more after the one before, by the loop's own signal (Android's Choreographer, the
  * web's animation frames, a Roku's render Timer). The refresh is the platform's where it says (`Platform.refreshRate`:
  * Android's display, the web's animation frames, a Roku's video mode where it names one); where it does not,
  * `unknownDropMs`, clear of a 30 Hz display's 33ms: a Roku TV drawing at 30 is not dropping every frame. The same
  * line counts the drops of every window, and so the step down.
  *
  * A run that has had an error nothing caught, or a change of screen that stalled, is not judged from then on: its
  * frames are of whatever was left on the screen, not of how the device copes.
  *
  * A summary of the session (`Perf.summaries`) goes out every `summarySeconds`, and as the app goes away. */
case class QualityPolicy(windowSeconds: Double = 60.0,
                         reducedKeyMs: Double = 300.0,
                         minimalKeyMs: Double = 600.0,
                         reducedDropPct: Double = 8.0,
                         minimalDropPct: Double = 20.0,
                         dropVsyncs: Double = 1.5,
                         unknownDropMs: Double = 40.0,
                         minKeys: Int = 3,
                         minFrames: Int = 30,
                         slowWindows: Int = 3,
                         summarySeconds: Double = 300.0,
                         steps: Boolean = true) {
  /** Where a frame counts as dropped, in ms from the one before, on a display drawn `refreshHz` times a second (0 where
    * that is not known). */
  def dropMs(refreshHz: Double): Double = if (refreshHz > 0.0) dropVsyncs * 1000.0 / refreshHz else unknownDropMs

  /** The level `w` calls for, from its own measures alone. */
  def wants(w: PerfWindow): QualityLevel =
    if (keyed(w, minimalKeyMs) || dropped(w, minimalDropPct) || w.memoryLevel == "critical") QualityLevel.Minimal
    else if (keyed(w, reducedKeyMs) || dropped(w, reducedDropPct) || w.memoryLevel == "low") QualityLevel.Reduced
    else QualityLevel.Full

  /** Why `w` calls for less than Full, in a few words ("" where it does not): `key p95 420ms >= 300`. */
  def why(w: PerfWindow): String = {
    val minimal = wants(w) == QualityLevel.Minimal
    val keyAt = if (minimal) minimalKeyMs else reducedKeyMs
    val dropAt = if (minimal) minimalDropPct else reducedDropPct
    List(
      if (keyed(w, keyAt)) "key p95 " + PerfWindow.whole(w.keyP95) + "ms >= " + PerfWindow.whole(keyAt) else "",
      if (dropped(w, dropAt)) "drop " + PerfWindow.whole(w.dropPct) + "% >= " + PerfWindow.whole(dropAt) else "",
      if (w.memoryLevel == "low" || w.memoryLevel == "critical") "memory " + w.memoryLevel else ""
    ).filter(_.nonEmpty).mkString(", ")
  }

  private def keyed(w: PerfWindow, ms: Double): Boolean = w.keys >= minKeys && w.keyP95 >= ms
  private def dropped(w: PerfWindow, pct: Double): Boolean = w.frames >= minFrames && w.dropPct >= pct
}
