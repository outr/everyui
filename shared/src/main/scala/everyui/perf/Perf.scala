package everyui.perf

import everyui.render.Platform
import everyui.scribe
import everyui.util.Clock
import reactify.*

/** How the app feels on this device, measured as it runs once `Quality.start` asks: from each key to the frame drawn
  * after it was handled, the app's own update in each frame (on a Roku, the BrightScript work), the time from frame to
  * frame and the frames dropped, the platform's own frame times where it has them (Android's FrameMetrics), and its
  * memory (a Roku's general memory level and the share of its limit in use, Android's low-memory state and heap). Each
  * is counted into buckets as it comes, never logged, and read out a window at a time (`QualityPolicy.windowSeconds`):
  * every window to `windows`, and the session so far to `summaries` every `QualityPolicy.summarySeconds` and as the app
  * goes away, so a session ended without warning has still said how it went.
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
    Platform.onAway(() => summarise())
    // Android's own frame times, heard on a thread of their own: a count lost to a window closing is of no matter
    Platform.frameTimings((ms: Double, _: Double) => if (ms < Perf.away) meter.platformFrame(ms))
    every()
    scribe.info("perf: measuring session " + id + " on " + Platform.kind.toString + " " + device.line)
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
    if (ms < away) meter.frame(ms, updateMs, Quality.policy.dropMs)
    if (now - meter.windowStart >= Quality.policy.windowSeconds * 1000.0) close(now)
  }

  /** The window under way, as it stands. */
  def current(): PerfWindow =
    meter.window(Platform.ticks(), Clock.real(), Platform.memoryLevel(), Platform.memoryUsedPct(), Quality())

  private def close(now: Double): Unit = {
    val w = meter.close(now, Clock.real(), Platform.memoryLevel(), Platform.memoryUsedPct(), Quality(), Quality.policy)
    if (logs) scribe.info("perf: " + w.line)
    windows @= w
    Quality.judge(w, (now - meter.started) / 1000.0)
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
      steppedDown = Some(Quality.steppedDown))
  }

  /** In a line, what the session saw: its windows and how many were slow, the step down if it took one, the device. */
  private def evidence(keys: Int, frames: Int): String =
    meter.windows.toString + " windows, " + meter.slowWindows.toString + " slow; " + keys.toString + " keys, " +
      frames.toString + " frames" + (if (Quality.steppedDown) "; stepped " + Quality.stepEvidence else "") +
      (if (device.line.isEmpty) "" else "; " + device.line)

  /** The session so far to `summaries`, where anything has been measured. */
  def summarise(): Unit = if (on && (meter.keyCount > 0 || meter.frameCount > 0)) summaries @= session()

  /** Measuring from `now` again, as at launch: a spec's. */
  private[everyui] def restart(now: Double): Unit = {
    meter = new PerfMeter(now)
    keyAt = -1.0
    keyStage = 0
  }
  /** A key pressed at `at`: a spec's. */
  private[everyui] def keyedAt(at: Double): Unit = if (keyStage == 0) {
    keyAt = at
    keyStage = 1
  }
}
