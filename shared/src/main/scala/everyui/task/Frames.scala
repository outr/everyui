package everyui.task

import everyui.render.Platform

/** When the app's frames run. Where the platform can (`onDemand`: the web, the desktop, Android), the frame loop runs
  * only while something needs it, and otherwise asks the platform for nothing: a screen showing what it last heard,
  * open for days, costs no frames at all, and a battery nothing. It runs again, on the platform's next frame (the
  * same one on the web), when:
  *
  *  - anything drawn changes (a component's property, its size, its children): [[wake]], by the components
  *    themselves, for the frame that draws it and the one after, by which it is on the screen;
  *  - somebody uses the app (a key, a pointer);
  *  - an animation starts (`Animation.start`, a task on a `TaskSupport`), for as long as it runs;
  *  - something listens to a `TaskSupport`'s `delta`, or says it needs frames (`Updates.needsFrames`), for as long as
  *    it does;
  *  - or something asks: [[request]] for a frame or a few, [[hold]] for every frame until it lets go.
  *
  * Motion (`Component.motion`) runs on the platform's own animation, with no frames of the app's. Where the platform
  * cannot (a Roku, an Apple TV), every frame runs, as it always has.
  *
  * Everything drawn is changed on the thread that draws, and so is the loop: a change made on another (a socket's,
  * a pool's) is a fault of the app's. Android's frames are asked for on its main thread whichever thread wakes the
  * loop, the first few such wakes logged with where they came from, and a frame that fails is logged and the loop
  * goes on.
  *
  * {{{
  *   // work done in a screen's `update` every frame, while a countdown shows
  *   override def needsFrames: Boolean = super.needsFrames || counting()
  *
  *   // or from anywhere, for as long as a drag is followed frame by frame
  *   val frames = Frames.hold()
  *   ...
  *   frames.release()
  * }}}
  */
object Frames {
  /** The loop's own frame, told when what woke it asked (`Platform.ticks()`), or -1 when it was already running. */
  private var tick: Double => Unit = (_: Double) => ()
  /** Whether the loop itself still wants frames: a task running, a listener on `delta`. */
  private var wanted: () => Boolean = () => false
  /** Told as the loop stops, to ask for the next frame it knows it will want (the clock's next second). */
  private var stopping: () => Unit = () => ()
  private var started = false
  /** Whether the loop runs on demand: all a wake has to look at where it does not (a Roku reads each of these through a
    * call of its own, on every change). */
  private var demand = false
  /** How the next frame is asked of the platform. */
  private var schedule: (() => Unit) => Unit = (f: () => Unit) => Platform.nextFrame(f)
  private var asleep = false
  /** How many frames are still owed to what asked for them. */
  private var left = 0
  private var holds = 0
  private var wokeAt = -1.0
  /** Which loop this is: a frame asked for by one since replaced is not run. */
  private var loop = 0
  /** Whether a frame is running now, and when the frame the loop waits for was asked. */
  private var stepping = false
  private var askedAt = 0.0
  /** Whether the frame waited for was asked by the frame before it, rather than by what woke the loop. */
  private var chained = false
  /** The first loop whose frame was given up on (`overdue`) and is not yet known to be lost or late, or -1; when that
    * frame was asked, and by what. One that comes after all says the thread was held up; the one asked in its place
    * coming first says it was lost. */
  private var givenUp = -1
  private var givenAt = 0.0
  private var givenBy = ""
  /** Where what woke the loop last came from, as [[origin]] had it. */
  private var wokeFrom: AnyRef = null

  /** Whether a frame asked for and not come after [[patience]] ms is given up on, and asked for again, at the next
    * wake, request or hold: the loop otherwise waits on it for ever, running as far as a wake can tell, and the app
    * stops moving (a scroll, an animation) while what is set at once still shows. Android's (`EveryUIActivity`). */
  private[everyui] var guarded: Boolean = false
  private[everyui] var patience: Double = 1000.0
  /** What is kept of where each wake from idle came from, for the report of a frame that never came (Android's: the
    * thread and its stack, printed only then). */
  private[everyui] var origin: () => AnyRef = () => null
  /** How many frames were given up on, and how many frames failed. */
  private[everyui] var lost: Int = 0
  private[everyui] var failed: Int = 0

  /** For a look at what keeps the loop from idling (Android's `everyui.render.IdleAudit`): the frames run, and those
    * run one after another since the loop last idled. */
  private[everyui] var stepped: Int = 0
  private[everyui] var streak: Int = 0
  /** Told of each wake, request and hold while `watched`: what asked, a diagnostic's. */
  private[everyui] var watched: Boolean = false
  private[everyui] var watcher: String => Unit = (_: String) => ()

  /** How many frames are still owed, how many holds are out, and whether the loop wants the next frame for itself. */
  private[everyui] def owed: Int = left
  private[everyui] def held: Int = holds
  private[everyui] def wants: Boolean = wanted()

  /** Whether frames run only while something needs one, rather than every frame the platform draws. */
  def onDemand: Boolean = Platform.framesOnDemand()

  /** Whether frames are running now: false while the loop is idle, waiting to be woken. */
  def running: Boolean = started && !asleep

  /** Something drawn changed: the frame that draws it runs, and the one after. What changes as a frame runs has those
    * two after it. Cheap when frames are running already, as it is called on every change. */
  def wake(): Unit = if (demand) {
    if (watched) watcher("wake")
    if (left < 2) left = 2
    if (asleep) resume() else if (guarded) overdue()
  }

  /** The next `count` frames run, whatever else needs them. */
  def request(count: Int = 1): Unit = {
    if (watched) watcher("request")
    if (left < count) left = count
    if (asleep) resume() else if (guarded) overdue()
  }

  /** Every frame runs until the hold is released. */
  def hold(): FrameHold = {
    if (watched) watcher("hold")
    holds += 1
    if (asleep) resume() else if (guarded) overdue()
    new FrameHold
  }

  /** The app is in front again (Android's `Platform.resumed`): a frame, so whatever moved on while it was away is
    * drawn, and a loop left waiting on a frame that never came runs again. */
  private[everyui] def resumed(): Unit = if (demand) request()

  private[task] def release(): Unit = holds -= 1

  /** Run the frame loop: `frame` on each frame, with when what woke it asked (-1 when the loop was running already);
    * `wants` says whether the loop needs the next frame for itself; `idle` is told each time the loop stops. The
    * screen manager runs it (`ScreenManager.run`). */
  def run(frame: Double => Unit, wants: () => Boolean, idle: () => Unit): Unit =
    if (onDemand) drive(frame, wants, idle, (f: () => Unit) => Platform.nextFrame(f))
    else {
      started = true
      tick = frame
      Platform.animate(() => every())
    }

  /** The loop on demand, with `ask` to ask for a frame: what `run` does with the platform's, and a spec with its own.
    * A loop already running is replaced. */
  private[everyui] def drive(frame: Double => Unit, wants: () => Boolean, idle: () => Unit,
                             ask: (() => Unit) => Unit): Unit = {
    started = true
    demand = true
    tick = frame
    wanted = wants
    stopping = idle
    schedule = ask
    asleep = false
    stepping = false
    wokeAt = -1.0
    loop += 1
    chained = false
    givenUp = -1
    next(loop)
  }

  /** The next frame of loop `of`. A platform that cannot ask for one (Android's Choreographer, asked on a thread
    * without a Looper) leaves the loop idle, for the next wake to ask again, rather than running with nothing asked. */
  private def next(of: Int): Unit = {
    askedAt = Platform.ticks()
    try schedule(() => if (of == loop) step() else if (givenUp >= 0 && of >= givenUp) late())
    catch {
      case t: Throwable =>
        asleep = true
        streak = 0
        everyui.scribe.error("frames: the next frame could not be asked for, the loop idles until woken: " + t.toString)
    }
  }

  /** One frame of the loop on demand, and the next asked for if anything needs it. A frame that fails is logged and
    * the loop goes on, as it would have without it: a loop stopped by one failure never ran again. */
  private def step(): Unit = {
    if (givenUp >= 0) confirmLost()
    val woke = wokeAt
    wokeAt = -1.0
    stepped += 1
    streak += 1
    if (left > 0) left -= 1
    stepping = true
    try tick(woke)
    catch {
      case t: Throwable =>
        failed += 1
        // its stack to the platform's log, and the failure to what hears the app's problems (Errors), or else to
        // wherever problems are sent (scribe.forward)
        if (everyui.error.Errors.listening) everyui.error.Errors.report("a frame", t)
        else {
          Platform.logError("frames: a frame failed", t)
          try everyui.scribe.forward("error", "frames: a frame failed: " + t.toString) catch { case _: Throwable => () }
        }
    }
    stepping = false
    chained = true
    if (holds > 0 || left > 0 || wanted()) next(loop)
    else {
      asleep = true
      streak = 0
      stopping()
    }
  }

  /** The frame the loop waits for, if it has not come in [[patience]] ms, given up on and asked for again: the one
    * asked may be lost (Android's, posted to a Choreographer that never ran it), or only late (the thread held up by a
    * long build). A warning says so, with where it was asked from; which it was is known when one of the two comes
    * (frames come in the order asked): the one given up on first is late, said as info ([[late]]), and the one asked
    * in its place first means it was lost, an error ([[step]]). A busy thread so costs one warning, and a frame lost
    * one warning and one error. Given up on again before either comes, the frame is asked again and nothing more is
    * said. */
  private def overdue(): Unit = if (demand && !stepping) {
    val waited = Platform.ticks() - askedAt
    if (waited > patience) {
      if (givenUp < 0) {
        lost += 1
        givenBy = if (chained) "by the frame before it"
          else "on waking, from " + (if (wokeFrom == null) "unknown" else wokeFrom.toString)
        everyui.scribe.warn("frames: a frame asked for " + waited.toInt.toString + " ms ago has not come, asked " +
          "again; it was asked " + givenBy)
        givenUp = loop
        givenAt = askedAt
      }
      loop += 1
      wokeAt = Platform.ticks()
      chained = false
      next(loop)
    }
  }

  /** A frame given up on came after all: the thread that runs frames was held up (a long build), not the frame lost.
    * Said once, as info, so the warning before it is read for what it was; the one asked in its place runs the loop
    * on. */
  private def late(): Unit = {
    givenUp = -1
    lost -= 1
    everyui.scribe.info("frames: the frame given up on came after all, " + (Platform.ticks() - givenAt).toInt.toString +
      " ms after it was asked: the thread was held up, not the frame lost")
  }

  /** The frame asked in place of one given up on came first: that one was lost, an error. */
  private def confirmLost(): Unit = {
    givenUp = -1
    everyui.scribe.error("frames: a frame asked for " + (Platform.ticks() - givenAt).toInt.toString + " ms ago never " +
      "came, the one asked in its place did; it was asked " + givenBy)
  }

  /** One frame of the loop where every frame runs. */
  private def every(): Unit = {
    if (left > 0) left -= 1
    tick(-1.0)
  }

  private def resume(): Unit = {
    asleep = false
    wokeAt = Platform.ticks()
    if (guarded) wokeFrom = origin()
    chained = false
    next(loop)
  }
}
