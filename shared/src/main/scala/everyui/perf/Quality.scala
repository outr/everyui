package everyui.perf

import everyui.component.Component
import everyui.scribe
import reactify.*

/** How much the app draws and moves: the level it is given at launch (`start`, the one its server keeps for the
  * device), lowered for the rest of the session where the session itself is slow for long enough (`policy`). It
  * never goes back up within a session, and nothing of it is kept here: the next launch starts from the level given.
  *
  * EveryUI's own parts follow it (`Full` changes nothing, and costs nothing per frame):
  *
  *  - animations (`x to 1.0 in 300.millis`, a task's fades and slides, a screen's treatment blending) take half as long
  *    at Reduced and a quarter at Minimal, and a fling as long as that; a ScrollContainer's glides take half as long at
  *    Reduced and jump at Minimal;
  *  - a box's shadow and blur go from Reduced; a text's shadow, and motion run by the platform (`Component.motion`), at
  *    Minimal;
  *  - fewer pictures are kept (Android's cache at half and then a quarter) and Android decodes downloads in 16 bits
  *    a pixel; a picture on the screen is never decoded smaller than it is drawn, which blurred posters asked at about
  *    their drawn size for little memory saved;
  *  - less is built and drawn beyond what shows: a ScrollContainer's slack beyond its viewport, a VirtualList's
  *    `overscan`.
  *
  * The app reads the same for its own costly parts, `Quality.level` as it changes, or `Quality.reduced` where it builds:
  *
  * {{{
  *   Quality.start(stored)                                   // the server's level for this device, or None for Full
  *   Perf.summaries.attach(s => Api.perfSession(s))          // how the session went, now and then and as it goes away
  *   follow(Quality.level, (l: QualityLevel) => particles.visible @= l == QualityLevel.Full)
  * }}} */
object Quality {
  private val running: Var[QualityLevel] = Var(QualityLevel.Full)
  /** The level the app runs at now. */
  val level: Val[QualityLevel] = running
  def apply(): QualityLevel = running()

  /** When the session steps down (each threshold, and how long it is measured): placeholders until a slow device's
    * numbers are in. */
  var policy: QualityPolicy = QualityPolicy()

  private var handed: QualityLevel = QualityLevel.Full
  private var floor: QualityLevel = QualityLevel.Full
  private var slowRun: Int = 0
  private var stepped: String = ""

  /** Run at `stored` (Full for None): the level kept for this device, with whatever the person chose folded in. From
    * now on the session is measured (`Perf`), steps itself down where it is slow for long enough, and says how it has
    * gone (`Perf.summaries`). Given again later (the server's answer come in after launch), the level given changes,
    * but never back above what the session has stepped down to, and the session goes on: the same `Perf.sessionId`, and
    * what was measured before it. One run of the app is one session, however many times it is started. */
  def start(stored: Option[QualityLevel]): Unit = {
    handed = stored.getOrElse(QualityLevel.Full)
    settle()
    Perf.begin()
  }

  /** The level `start` was given. */
  def givenLevel: QualityLevel = handed
  /** Whether the session stepped down for itself, and in a few words why: `Full -> Reduced at 184s: key p95 420ms >=
    * 300 (3 windows)`. */
  def steppedDown: Boolean = stepped.nonEmpty
  def stepEvidence: String = stepped

  /** Whether the app runs below Full, or at Minimal: for what is built once. */
  def reduced: Boolean = running() != QualityLevel.Full
  def minimal: Boolean = running() == QualityLevel.Minimal

  /** A window closed (`Perf`): a run of them calling for less than the session runs at steps it down one level. */
  private[perf] def judge(w: PerfWindow, at: Double): Unit = if (policy.steps) {
    val now = running()
    if (policy.wants(w).ordinal > now.ordinal) slowRun += 1 else slowRun = 0
    if (slowRun >= policy.slowWindows && now != QualityLevel.Minimal) {
      slowRun = 0
      floor = if (now == QualityLevel.Full) QualityLevel.Reduced else QualityLevel.Minimal
      stepped = now.toString + " -> " + floor.toString + " at " + PerfWindow.whole(at) + "s: " + policy.why(w) + " (" +
        policy.slowWindows.toString + " windows)"
      settle()
      scribe.warn("quality: stepped down " + stepped + " | " + w.line + " | " + Perf.device.line)
    }
  }

  private def settle(): Unit = {
    val l = if (floor.ordinal > handed.ordinal) floor else handed
    if (l != running()) running @= l
  }

  /** As though nothing had been given or stepped: a spec's. */
  private[everyui] def forget(): Unit = {
    handed = QualityLevel.Full
    floor = QualityLevel.Full
    slowRun = 0
    stepped = ""
    policy = QualityPolicy()
    settle()
  }

  // what EveryUI's parts read, set as the level changes, so a read is a plain field's (a frame reads some of them)
  /** How long an animation takes, as a share of its own time. */
  private[everyui] var pace: Double = 1.0
  /** Whether a scroll glides, rather than jumping. */
  private[everyui] var glides: Boolean = true
  /** How much beyond its viewport a ScrollContainer draws, in viewports each way. */
  private[everyui] var slack: Double = 0.5
  /** Whether downloaded pictures are decoded in 16 bits a pixel where they have no transparency (Android's). */
  private[everyui] var compactPixels: Boolean = false
  /** The share of their budget kept pictures may hold (Android's). */
  private[everyui] var cache: Double = 1.0
  /** Whether a box's shadow and blur, a text's shadow, and motion the platform runs, are drawn. */
  private[everyui] var effects: Boolean = true
  private[everyui] var textEffects: Boolean = true
  private[everyui] var moves: Boolean = true
  /** How many times the level has changed: what was drawn before the last is drawn again as it is shown. */
  private[everyui] var changes: Int = 0

  /** How many rows a list builds beyond each end of what shows, where it would build `rows`. */
  def overscan(rows: Int): Int =
    if (pace >= 1.0) rows else if (glides) (rows + 1) / 2 else math.min(rows, 1)

  running.attach { l =>
    val full = l == QualityLevel.Full
    val least = l == QualityLevel.Minimal
    pace = if (full) 1.0 else if (least) 0.25 else 0.5
    glides = !least
    slack = if (full) 0.5 else if (least) 0.1 else 0.25
    compactPixels = !full
    cache = if (full) 1.0 else if (least) 0.25 else 0.5
    effects = full
    textEffects = !least
    moves = !least
    // what is already drawn with them follows too, and what is not on the screen as it comes on
    changes += 1
    Component.requality(everyui.ui)
  }
}
