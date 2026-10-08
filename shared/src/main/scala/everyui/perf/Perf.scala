package everyui.perf

import everyui.render.Platform
import everyui.scribe
import everyui.util.Clock
import reactify.*

/** How the app feels on this device, measured as it runs once `Quality.start` asks: from each key to the frame drawn
  * after it was handled, the app's own update in each frame (on a Roku, the BrightScript work), the time from frame to
  * frame and the frames dropped (a vsync or more late, `QualityPolicy.dropMs`), the platform's own frame times where it
  * has them (Android's FrameMetrics), its memory (a Roku's general memory level and the share of its limit in use,
  * Android's low-memory state and heap), and what went wrong: errors nothing caught, a change of screen that stalled, a
  * turn the device cut off. Each is counted into buckets as it comes, never logged, and read out a window at a time
  * (`QualityPolicy.windowSeconds`): every window to `windows`, and the session so far to `summaries` every
  * `QualityPolicy.summarySeconds` and as the app goes away, so a session ended without warning has still said how it
  * went. A run that has gone wrong is judged no longer (`Quality`'s step down): its frames are of whatever was left on
  * the screen.
  *
  * Launched with `perf=1` (`?perf=1`, `--perf=1`, `--es perf 1`, a Roku's launch parameter), each window is logged in a
  * line. `current` is the window under way, for an overlay. */
object Perf {
  /** Whether it is measuring: from `Quality.start`. Nothing is measured, at no cost, until then. */
  private[everyui] var on: Boolean = false
  private var meter: PerfMeter = new PerfMeter(0.0)
  /** When the key being timed was pressed, and how far it has got: 1 pressed, 2 its frame begun. */
  private var keyAt: Double = -1.0
  private var keyStage: Int = 0
  private var id: String = ""
  private var logs: Boolean = false
  /** A frame further apart than this, in ms, is the app having been away. */
  private[perf] val away: Double = 5000.0
  /** Where a frame counts as dropped, in ms (`QualityPolicy.dropMs`), worked out again as each window closes: the web
    * reads its refresh from the frames it runs. */
  private var dropLine: Double = 40.0
  /** The display's refresh a spec gives, in place of the platform's; -1 for the platform's. */
  private var assumedHz: Double = -1.0
  /** The changes of screen that stalled; the errors and the turns cut off there had been before measuring began; and
    * whether the session has said it is judged no longer. */
  private var stalls: Int = 0
  private var errorsBefore: Int = 0
  private var cutBefore: Int = 0
  private var excused: Boolean = false

  /** Each window as it closes. */
  val windows: Channel[PerfWindow] = Channel[PerfWindow]
  /** The session so far, now and then (`QualityPolicy.summarySeconds`) and as the app goes away: what to send to
    * where the device's level is decided. */
  val summaries: Channel[PerfSession] = Channel[PerfSession]

  /** What the device said of itself at launch: nothing, where it could not be asked. */
  lazy val device: DeviceHint = try Platform.deviceHint() catch { case _: Throwable => DeviceHint() }

  /** This run's own id, the same in each of its summaries. */
  def sessionId: String = id

  private[perf] def begin(): Unit = if (!on) {
    on = true
    meter = new PerfMeter(Platform.ticks())
    id = Clock.real().toString + "-" + (Platform.random() * 1.0e9).toLong.toString
    logs = Platform.launchArg("perf") == "1"
    errorsBefore = seenErrors()
    cutBefore = Platform.turnsCutOff()
    dropLine = line()
    Platform.onAway(() => summarise())
    // Android's own frame times, heard on a thread of their own: a count lost to a window closing is of no matter
    Platform.frameTimings((ms: Double, _: Double) => if (ms < Perf.away) meter.platformFrame(ms))
    every()
    scribe.info("perf: measuring session " + id + " on " + Platform.kind.toString + " " + device.line +
      ", a frame dropped at " + PerfWindow.whole(dropLine) + "ms")
  }
  else {
    // started again (the server's level come in): the same session goes on, its id and its measures, said so in the log
    scribe.info("perf: session " + id + " goes on, given " + Quality.givenLevel.toString + ", at " + Quality().toString)
  }

  private def every(): Unit = Platform.after(Quality.policy.summarySeconds, () => {
    summarise()
    every()
  })

  /** A key pressed: timed to the frame drawn after it was handled. A key pressed before the last one's frame is drawn
    * waits with it, and is timed from the first. */
  private[everyui] def keyed(): Unit = if (on && keyStage == 0) {
    keyAt = Platform.ticks()
    keyStage = 1
  }

  /** A frame of the loop begun at `now`, `ms` after the one before, which spent `updateMs` in the app's update. A key's
    * time runs to the start of the frame after the one that drew it, which is when that one was on the screen. */
  private[everyui] def frame(ms: Double, updateMs: Double, now: Double): Unit = {
    if (keyStage == 2) {
      meter.key(now - keyAt)
      keyStage = 0
    }
    else if (keyStage == 1) keyStage = 2
    // a gap this long is the app away, not a frame it drew
    if (ms < away) meter.frame(ms, updateMs, dropLine)
    if (now - meter.windowStart >= Quality.policy.windowSeconds * 1000.0) close(now)
  }

  /** The window under way, as it stands. */
  def current(): PerfWindow =
    meter.window(Platform.ticks(), Clock.real(), Platform.memoryLevel(), Platform.memoryUsedPct(), Quality())

  private def close(now: Double): Unit = {
    val w = meter.close(now, Clock.real(), Platform.memoryLevel(), Platform.memoryUsedPct(), Quality(), Quality.policy)
    if (logs) scribe.info("perf: " + w.line)
    windows @= w
    dropLine = line()
    // a run that has gone wrong is judged no longer: its frames are of whatever was left on the screen
    val wrong = trouble()
    if (wrong.isEmpty) Quality.judge(w, (now - meter.started) / 1000.0)
    else if (!excused) {
      excused = true
      scribe.info("quality: no longer judged in this run, which " + wrong)
    }
  }

  /** Where a frame counts as dropped now. */
  private def line(): Double = Quality.policy.dropMs(if (assumedHz >= 0.0) assumedHz else Platform.refreshRate())

  /** A change of screen that never finished (`ScreenManager.stallSeconds`). */
  private[everyui] def stalled(): Unit = stalls += 1

  /** The errors nothing caught since measuring began: frames that failed, and what the platform saw. */
  def errors(): Int = math.max(0, seenErrors() - errorsBefore)
  private def seenErrors(): Int = everyui.task.Frames.failed + math.max(0, Platform.errorsSeen())

  /** Whether a change of screen stalled, or the device cut a turn off, since measuring began. */
  def hasStalled: Boolean = stalls > 0 || Platform.turnsCutOff() > cutBefore

  /** What went wrong in the run, in a few words ("had 2 errors, stalled"), or "" where nothing did. */
  private def trouble(): String = {
    val errs = errors()
    List(if (errs > 0) "had " + errs.toString + (if (errs == 1) " error" else " errors") else "",
      if (hasStalled) "stalled" else "").filter(_.nonEmpty).mkString(", ")
  }

  /** The session so far. */
  def session(): PerfSession = {
    val keys = meter.keyCount
    val frames = meter.frameCount
    val memory = Platform.memoryLevel()
    val used = Platform.memoryUsedPct()
    PerfSession(
      sessionId = id,
      at = Clock.real(),
      sessionSeconds = Some(((Platform.ticks() - meter.started) / 1000.0).toInt),
      keyLatencyMsP50 = if (keys > 0) Some(math.round(meter.keyPercentile(0.5)).toInt) else None,
      keyLatencyMsP95 = if (keys > 0) Some(math.round(meter.keyPercentile(0.95)).toInt) else None,
      frameDropPct = if (frames > 0) Some(math.round(meter.dropPct).toInt) else None,
      memoryLevel = if (memory.isEmpty) None else Some(memory),
      evidence = Some(evidence(keys, frames)),
      keys = Some(keys),
      frames = Some(frames),
      updateMsP95 = if (frames > 0) Some(math.round(meter.updatePercentile(0.95)).toInt) else None,
      frameMsP95 = if (frames > 0) Some(math.round(meter.framePercentile(0.95)).toInt) else None,
      memoryUsedPct = if (used >= 0) Some(used) else None,
      platform = Some(Platform.kind.toString),
      model = device.model,
      graphicsPlatform = device.graphicsPlatform,
      ramMb = device.ramMb,
      levelGiven = Some(Quality.givenLevel.toString),
      levelRun = Some(Quality().toString),
      steppedDown = Some(Quality.steppedDown),
      errors = Some(errors()),
      stalled = Some(hasStalled))
  }

  /** In a line, what the session saw: its windows and how many were slow, its keys and frames and where a frame was
    * dropped, what went wrong, the step down if it took one, the device. */
  private def evidence(keys: Int, frames: Int): String = {
    val wrong = trouble()
    meter.windows.toString + " windows, " + meter.slowWindows.toString + " slow; " + keys.toString + " keys, " +
      frames.toString + " frames, dropped at " + PerfWindow.whole(dropLine) + "ms" +
      (if (wrong.isEmpty) "" else "; " + wrong) + (if (Quality.steppedDown) "; stepped " + Quality.stepEvidence else "") +
      (if (device.line.isEmpty) "" else "; " + device.line)
  }

  /** The session so far to `summaries`, where anything has been measured. */
  def summarise(): Unit = if (on && (meter.keyCount > 0 || meter.frameCount > 0)) summaries @= session()

  /** Measuring from `now` again, as at launch: a spec's. */
  private[everyui] def restart(now: Double): Unit = {
    meter = new PerfMeter(now)
    keyAt = -1.0
    keyStage = 0
    stalls = 0
    errorsBefore = seenErrors()
    cutBefore = Platform.turnsCutOff()
    excused = false
    dropLine = line()
  }
  /** The display taken to refresh `hz` times a second (0: not known), or the platform's again for -1: a spec's. */
  private[everyui] def assumeRefresh(hz: Double): Unit = {
    assumedHz = hz
    dropLine = line()
  }
  /** A key pressed at `at`: a spec's. */
  private[everyui] def keyedAt(at: Double): Unit = if (keyStage == 0) {
    keyAt = at
    keyStage = 1
  }
}
