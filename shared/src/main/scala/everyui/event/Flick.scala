package everyui.event

import everyui.render.Platform

/** A finger dragging scrolling content, and the glide it carries on with once it lifts, as a phone's own lists do: what
  * every scrolling surface does under a finger. `down` where the finger goes down, `at` each place it moves to, `lift`
  * as it lifts; places are where the finger is along the drag, which moves the content's offset the other way (a
  * finger moving up the screen scrolls a list down: its places are its y; along a rail run from the right, minus its
  * x). The glide scrolls the content by `by`, which scrolls that far and answers
  * how far it went, and stops at an end, a finger put down on it, or `stop`. */
class Flick(by: Double => Double) {
  private val fling = new Fling
  private var run: Option[FlingRun] = None

  /** Whether a glide is still carrying the content on. */
  def gliding: Boolean = run.exists(_.gliding)

  /** The glide stopped where it is: a finger put down, the wheel, or the content scrolled some other way. */
  def stop(): Unit = {
    run.foreach(_.stop())
    run = None
  }

  /** A finger down at `place`: a glide under way stops where it is, and the drag starts again from here. */
  def down(place: Double): Unit = {
    stop()
    fling.clear()
    fling.at(place, Flick.ticks())
  }

  /** The finger at `place`. */
  def at(place: Double): Unit = fling.at(place, Flick.ticks())

  /** The finger lifted: the content glides on from how fast it was going, unless it had stopped before it lifted. */
  def lift(): Unit = {
    val v = fling.velocity(Flick.ticks())
    if (v != 0.0) {
      val glide = new FlingRun(-v, by)
      run = Some(glide)
      glide.start()
    }
  }
}

object Flick {
  /** The clock a finger's moves are timed by: `Platform.ticks`, which a spec may set. */
  private[everyui] var ticks: () => Double = () => Platform.ticks()
}
