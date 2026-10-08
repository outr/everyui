package everyui.component.types

import reactify.{Val, Var}

/** youi's `SizeProperty`: follows the component's `measured` size until something sets it explicitly, after which
  * every value is written out to the node. */
class SizeProperty(measured: Val[Double], write: Double => Unit) extends Var[Double](measured()) {
  private var explicitlySet = false
  /** Whether something has set this, rather than it following the measured size. */
  def explicit: Boolean = explicitlySet

  attach { d =>
    if (explicitlySet) writeOut(d)
  }

  // written out, and drawn: the frame loop runs, if it was idle (Frames)
  private def writeOut(d: Double): Unit = {
    write(d)
    everyui.task.Frames.wake()
  }

  override def @=(value: Double): Unit = {
    val first = !explicitlySet
    explicitlySet = true
    // the first explicit value must reach the node even if it equals the measured one, and must stop following it
    // either way: kept bound, a spinner made 18 square, as its glyph measured, went to 0 by 0 when it was measured
    // again hidden
    val same = get == value
    super.@=(value)
    if (first && same) writeOut(value)
  }

  override def :=(value: => Double): Unit = {
    val first = !explicitlySet
    explicitlySet = true
    val before = get
    super.:=(value)
    if (first && get == before) writeOut(get)
  }
}
