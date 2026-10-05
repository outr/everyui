package everyui.event

import everyui.render.Platform

/** A flick carried on after the finger lifts (`Fling`): from `velocity` (pixels a millisecond, along the content's
  * scroll), a step each of the platform's frames to `by`, which scrolls that far and answers how far it went, slowing
  * until it comes to rest. It stops where `by` went less than half its step, which is an end of the content: there it
  * stops, at the end. It runs on the platform's frames itself, not as a task, which only an app with screens steps. */
class FlingRun(velocity: Double, by: Double => Double) {
  private var v = velocity
  private var last = -1.0
  private var over = false

  /** Whether it is still carrying the content on. */
  def gliding: Boolean = !over

  /** Stopped where it is: a finger put down on it, or the content scrolled some other way. */
  def stop(): Unit = over = true

  /** Under way, a step a frame until it comes to rest or is stopped. Where a scroll does not glide (Minimal,
    * `everyui.perf.Quality`) it goes all the way at once. */
  def start(): FlingRun = {
    if (everyui.perf.Quality.glides) Platform.nextFrame(() => frame())
    else {
      by(Fling.distance(v))
      over = true
    }
    this
  }

  private def frame(): Unit = if (!over) {
    val now = Platform.ticks()
    // the time since the last step as the clock has it; no more than a tenth of a second, so a frame that stalled
    // does not throw the content a long way
    val ms = if (last < 0.0) 1000.0 / 60.0 else math.max(0.0, math.min(100.0, now - last))
    last = now
    if (advance(ms)) Platform.nextFrame(() => frame())
  }

  /** `ms` on: the step it goes to `by`, and whether it goes on after it. */
  def advance(ms: Double): Boolean = !over && {
    // as far, sooner, where animations take less time (`everyui.perf.Quality`)
    val (step, after) = Fling.step(v, ms / everyui.perf.Quality.pace)
    v = after
    val went = if (step == 0.0) 0.0 else by(step)
    over = math.abs(v) < Fling.rest || math.abs(went) < math.abs(step) / 2.0
    !over
  }
}
