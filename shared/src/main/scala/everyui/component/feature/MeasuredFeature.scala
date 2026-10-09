package everyui.component.feature

import reactify.{Val, Var}
import everyui.component.Component
import everyui.render.Platform

/** youi's `MeasuredFeature`: the node's laid-out size, refreshed whenever the component's `measure` fires (and, for a
  * component inside a container, whenever the container's does: see `ContainerFeature`).
  *
  * Where the platform reads sizes in batches (the web: `Platform.measuresInBatches`), a refresh is queued and every one
  * asked for in a turn is read together before the frame is drawn — all the reads, then all the sizes set — rather
  * than each read forcing the browser to lay the page out again after the change before it. */
class MeasuredFeature(private[feature] val component: Component) {
  private val w: Var[Double] = MeasuredFeature.size(this, across = true)
  private val h: Var[Double] = MeasuredFeature.size(this, across = false)
  // queued for the turn's batch, and the size its read found, until the batch sets it
  private[feature] var queued: Boolean = false
  private[feature] var readWidth: Double = 0.0
  private[feature] var readHeight: Double = 0.0
  // asked for out of the page and not read since, and read since (`peek`) but not yet set
  private var stale: Boolean = false
  private var peeked: Boolean = false
  // the width last set, read without reading `width` (which, in a value being worked out, would make it depend on it)
  private var setWidth: Double = 0.0

  val width: Val[Double] = w
  val height: Val[Double] = h

  /** Read the node's size: now, or with the rest of the turn's where the platform batches them (the web). A node not
    * in the page yet (a screen being built, before it arrives) is read the moment its size is read (`peek`), lent to
    * the page for it, so a size read as it is made is the size it has, where the batch would find it still out of the
    * page and read 0; else it waits for the batch, by when it is usually in the page. A view built out of the page and
    * put in it in the same turn is then read there, once, with everything else, where reading each node as it was asked
    * for laid the page out again for every read, and again for each container the node went into: a chat's transcript of
    * long answers took seconds to build. */
  def refresh(): Unit = if (!Platform.measuresInBatches) {
    read()
    set()
  } else {
    if (!component.node.onPage) stale = true
    peeked = false
    MeasuredFeature.enqueue(this)
  }

  /** The size `across` (the width) or down as it is now, where it was asked for out of the page and has not been read
    * since: read for the asking, though only the batch sets it (a value set as it is read is heard in the middle of
    * hearing another, which cuts that short); otherwise `set`, the size as last set. (Put in the page since, it is read
    * with the batch, as a size asked for in the page is.) */
  private[feature] def peek(across: Boolean, set: Double): Double = {
    if (stale && !MeasuredFeature.quiet && !component.node.onPage) {
      stale = false
      read()
      peeked = true
    }
    if (!peeked) set else if (across) readWidth else readHeight
  }

  private[feature] def read(): Unit = {
    component.beforeRead()
    readWidth = component.node.getWidth()
    readHeight = component.node.getHeight()
  }

  private[feature] def set(): Unit = {
    stale = false
    peeked = false
    val wider = readWidth != setWidth
    setWidth = readWidth
    w @= readWidth
    h @= readHeight
    // (words look at their box again: TextView)
    if (wider) component.widthRemeasured()
  }

  component.measure.on(refresh())
}

object MeasuredFeature {
  // the refreshes asked for this turn, newest first, and whether their batch is scheduled
  private var pending: List[MeasuredFeature] = Nil
  private var scheduled: Boolean = false
  private var flushing: Boolean = false
  // rounds in one batch before the rest waits for the next: sizes that go on changing each other do not hold the page
  private val MaxRounds = 64

  /** Whether every size asked for has been read: always, where sizes are read as they are asked for; on the web, not
    * from a change that asks for one until the turn's batch has read and set them all. A size bound to others read before
    * then is a step on the way (a message's bubble with its words still 0 tall). */
  def settled: Boolean = pending.isEmpty && !flushing

  // how deep in `withBatch` (or the making of a size that follows the measured one, which reads it as it is: what reads
  // that size peeks first)
  private var holding: Int = 0
  private def quiet: Boolean = holding > 0

  /** `f`, with the sizes it reads of nodes out of the page read as they were last set, and read with the turn's batch:
    * for what it makes to put in the page before the turn is over (a list's rows), whose sizes are then read there, with
    * everything else, rather than one by one, out of it, as they are read while it is made. */
  def withBatch[T](f: => T): T = {
    holding += 1
    try f finally holding -= 1
  }
  private[feature] def quietly[T](f: => T): T = withBatch(f)

  // a measured size: on the web, one asked for out of the page and not read since is read as it is read (`peek`)
  private def size(m: MeasuredFeature, across: Boolean): Var[Double] =
    if (!Platform.measuresInBatches) Var(0.0) else new Var[Double](0.0) {
      override def get: Double = m.peek(across, super.get)
    }

  private def enqueue(m: MeasuredFeature): Unit = if (!m.queued) {
    m.queued = true
    pending = m :: pending
    if (!scheduled) {
      scheduled = true
      Platform.beforePaint(() => flush())
    }
  }

  /** Read every queued size, then set them, in the order they were asked for, and lay out what they moved; what
    * setting them asks to be measured again is the next round, in the same batch. Those in the page are read together,
    * then any still out of it, each lent to the page for its read. */
  private def flush(): Unit = {
    var rounds = 0
    flushing = true
    try {
      while (pending.nonEmpty && rounds < MaxRounds) {
        val round = pending.reverse
        pending = Nil
        round.foreach(_.queued = false)
        round.foreach(m => if (m.component.node.onPage) m.read())
        round.foreach(m => if (!m.component.node.onPage) m.read())
        // laid out once they are all set, not once for each: a column of lines measured together was laid out again
        // for every line
        everyui.layout.LayoutPass.batch(round.foreach(_.set()))
        rounds += 1
      }
    } finally flushing = false
    scheduled = false
    if (pending.nonEmpty) {
      scheduled = true
      Platform.beforePaint(() => flush())
    }
  }
}
