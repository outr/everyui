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
    if (asleep) resume()
  }

  /** The next `count` frames run, whatever else needs them. */
  def request(count: Int = 1): Unit = {
    if (watched) watcher("request")
    if (left < count) left = count
    if (asleep) resume()
  }

  /** Every frame runs until the hold is released. */
  def hold(): FrameHold = {
    if (watched) watcher("hold")
    holds += 1
    if (asleep) resume()
    new FrameHold
  }

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
    wokeAt = -1.0
    loop += 1
    next(loop)
  }

  /** The next frame of loop `of`. */
  private def next(of: Int): Unit = schedule(() => if (of == loop) step())

  /** One frame of the loop on demand, and the next asked for if anything needs it. */
  private def step(): Unit = {
    val woke = wokeAt
    wokeAt = -1.0
    stepped += 1
    streak += 1
    if (left > 0) left -= 1
    tick(woke)
    if (holds > 0 || left > 0 || wanted()) next(loop)
    else {
      asleep = true
      streak = 0
      stopping()
    }
  }

  /** One frame of the loop where every frame runs. */
  private def every(): Unit = {
    if (left > 0) left -= 1
    tick(-1.0)
  }

  private def resume(): Unit = {
    asleep = false
    wokeAt = Platform.ticks()
    next(loop)
  }
}
