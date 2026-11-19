package everyui.perf

import everyui.image.ImageBudget
import everyui.render.Platform

/** The frames and the memory of the run, said in two lines every `windowSeconds` (10) while it is on, on every
  * platform alike: the frames of the screen manager's loop (the web's animation frames, Android's Choreographer, a
  * Roku's render Timer), each timed from the start of the one before, its own update the "build" and the rest the
  * "raster", as NaboTV's FrameWatch writes them:
  *
  * {{{
  *   frames: 4/212 slow (2%) worst 84.3ms = build 40.1ms + raster 44.2ms screen 1920x1080@2.0, 9 dropped (4%) at 25ms
  *   memory: heap 41.2 of 2048 MB (2%), 1830 nodes, 412 components, pictures 18.4 MB in 23, 4 kept out of sight 6.3 MB
  * }}}
  *
  * A frame is slow at `slowMs` (32) or more, and dropped where it missed a vsync of the display's own refresh
  * (`QualityPolicy.dropMs`, as `Perf` counts it); the worst is the longest. Memory is what the platform says
  * (`Platform.memoryNote`: the web's script heap from `performance.memory` where the engine has it and the page's
  * elements; the desktop's and Android's heap; a Roku's share of its limit), the components made and not yet disposed
  * of (`Component.liveNow`), and the pictures drawn and kept out of sight (`ImageBudget`), 4 bytes a pixel at their
  * own size. A window with no frames (nothing moved) says nothing.
  *
  * Off by default, at the cost of a test of `on` a frame. On with `perf=1` at launch (`?perf=1`, `--perf=1`, `--es
  * perf 1`, a Roku's launch parameter), as `Perf`'s windows are, or `start()` from the app (a hidden switch, a
  * television's debug build). While it is on, pictures far larger than they are drawn are said too (`images:`). */
object FrameLog {
  /** Whether it logs. */
  private[everyui] var on: Boolean = Platform.launchArg("perf") == "1"
  /** How long a window is, in seconds. */
  var windowSeconds: Double = 10.0
  /** A frame this long or longer, in ms, is slow. */
  var slowMs: Double = 32.0

  private var from: Double = -1.0
  private var frames: Int = 0
  private var slow: Int = 0
  private var dropped: Int = 0
  private var worst: Double = 0.0
  private var worstUpdate: Double = 0.0
  private var dropAt: Double = 0.0

  /** Logging from now on. */
  def start(): Unit = on = true
  /** No more. */
  def stop(): Unit = {
    on = false
    from = -1.0
  }

  /** A frame `ms` after the one before, `updateMs` of it the app's update, begun at `now` (the platform's ticks). */
  private[everyui] def frame(ms: Double, updateMs: Double, now: Double): Unit = {
    if (from < 0.0) open(now)
    // a gap this long is the app having been away, not a frame it drew
    if (ms < Perf.away) {
      frames += 1
      if (ms >= slowMs) slow += 1
      if (ms >= dropAt) dropped += 1
      if (ms > worst) {
        worst = ms
        worstUpdate = updateMs
      }
    }
    if (now - from >= windowSeconds * 1000.0) {
      if (frames > 0) {
        Platform.log(framesLine())
        Platform.log(memoryLine())
      }
      open(now)
    }
  }

  private def open(now: Double): Unit = {
    from = now
    frames = 0
    slow = 0
    dropped = 0
    worst = 0.0
    worstUpdate = 0.0
    dropAt = Quality.policy.dropMs(Platform.refreshRate())
  }

  /** The frames of the window under way, in a line. */
  def framesLine(): String = {
    val ratio = Platform.pixelRatio()
    val screen = math.round(everyui.ui.size.width() * ratio).toString + "x" +
      math.round(everyui.ui.size.height() * ratio).toString + "@" + FrameLog.tenths(ratio)
    "frames: " + slow.toString + "/" + frames.toString + " slow (" + FrameLog.pct(slow, frames) + "%) worst " +
      FrameLog.tenths(worst) + "ms = build " + FrameLog.tenths(worstUpdate) + "ms + raster " +
      FrameLog.tenths(math.max(0.0, worst - worstUpdate)) + "ms screen " + screen + ", " + dropped.toString +
      " dropped (" + FrameLog.pct(dropped, frames) + "%) at " + math.round(dropAt).toString + "ms"
  }

  /** The memory now, in a line. */
  def memoryLine(): String = {
    val note = Platform.memoryNote()
    val used = Platform.memoryUsedPct()
    val platform = if (note.nonEmpty) note + ", " else if (used >= 0) "used " + used.toString + "%, " else ""
    "memory: " + platform + everyui.component.Component.liveNow.toString + " components, pictures " +
      FrameLog.megabytes(ImageBudget.heldBytes) + " MB in " + ImageBudget.held.toString + ", " +
      ImageBudget.keptCount.toString + " kept out of sight " + FrameLog.megabytes(ImageBudget.keptBytes) + " MB"
  }

  /** Milliseconds to one decimal place. */
  def tenths(ms: Double): String = {
    val t = math.round(ms * 10.0)
    (t / 10L).toString + "." + (t % 10L).toString
  }

  /** Bytes as megabytes to one decimal place. */
  def megabytes(bytes: Double): String = tenths(bytes / 1048576.0)

  /** `part` of `whole` as a whole percentage. */
  private def pct(part: Int, whole: Int): String =
    if (whole <= 0) "0" else math.round(part.toDouble * 100.0 / whole.toDouble).toString
}
