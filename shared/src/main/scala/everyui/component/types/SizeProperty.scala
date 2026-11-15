package everyui.component.types

import reactify.{Val, Var}

import scala.util.Success

/** youi's `SizeProperty`: follows the component's `measured` size until something sets it explicitly, after which
  * every value is written out to the node.
  *
  * A component's own size (`ComponentSize`) is given no `measured` to follow: it is told the size it measures as that
  * is read (`follow`), and takes it until something sets it or binds it, without what following a value costs (a
  * function reading it, a reaction on it, the set of what it reads, the value before). */
class SizeProperty(measured: Val[Double], write: Double => Unit) extends Var[Double]() {
  private var explicitlySet = false
  // whether it takes the size it is told it measures (`follow`): one with nothing to follow, until it is set or bound
  private var following = measured == null
  if (measured != null) set(measured())

  /** Whether something has set this, rather than it following the measured size. */
  def explicit: Boolean = explicitlySet

  // heard by a reaction of its own (`SizeWrite`), not a function attached to it, which cost a closure and its scope
  reactions += new SizeWrite(this)

  private[types] def written(d: Double): Unit = {
    if (explicitlySet) writeOut(d)
    changed()
  }

  /** Its first value, for a size with nothing to follow. */
  protected def start(first: Double): Unit = {
    state = Success(first)
  }

  /** The size it measures, for a size with nothing to follow: taken, unless something has set it or bound it since. */
  protected def follow(d: Double): Unit = if (following) super.static(d)

  /** The size written out to the node: `write`, unless a kind of size writes it itself. */
  protected def writeTo(d: Double): Unit = write(d)

  /** Heard as it changes, after it is written out: for a kind of size that tells what it sizes. */
  protected def changed(): Unit = ()

  // written out, and drawn: the frame loop runs, if it was idle (Frames)
  private def writeOut(d: Double): Unit = {
    writeTo(d)
    everyui.task.Frames.wake()
  }

  // set or bound, by any way there is to, it no longer takes what it is told it measures
  override def set(value: => Double): Unit = {
    following = false
    super.set(value)
  }

  override def static(value: Double): Unit = {
    following = false
    super.static(value)
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
