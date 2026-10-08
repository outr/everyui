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
class MeasuredFeature(component: Component) {
  private val w: Var[Double] = Var(0.0)
  private val h: Var[Double] = Var(0.0)
  // queued for the turn's batch, and the size its read found, until the batch sets it
  private[feature] var queued: Boolean = false
  private[feature] var readWidth: Double = 0.0
  private[feature] var readHeight: Double = 0.0
  // the width last set, read without reading `width` (which, in a value being worked out, would make it depend on it)
  private var setWidth: Double = 0.0

  val width: Val[Double] = w
  val height: Val[Double] = h

  /** Read the node's size: now, or with the rest of the turn's where the platform batches them (the web). A node
    * not in the page yet (a screen being built over turns, before it arrives) is read at once, lent to the page for it:
    * the batch would find it still out of the page, and a size read as it is made would be 0. */
  def refresh(): Unit = if (Platform.measuresInBatches && component.node.onPage) MeasuredFeature.enqueue(this) else {
    read()
    set()
  }

  private[feature] def read(): Unit = {
    component.beforeRead()
    readWidth = component.node.getWidth()
    readHeight = component.node.getHeight()
  }

  private[feature] def set(): Unit = {
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
  // rounds in one batch before the rest waits for the next: sizes that go on changing each other do not hold the page
  private val MaxRounds = 64

  private def enqueue(m: MeasuredFeature): Unit = if (!m.queued) {
    m.queued = true
    pending = m :: pending
    if (!scheduled) {
      scheduled = true
      Platform.beforePaint(() => flush())
    }
  }

  /** Read every queued size, then set them, in the order they were asked for, and lay out what they moved; what
    * setting them asks to be measured again is the next round, in the same batch. */
  private def flush(): Unit = {
    var rounds = 0
    while (pending.nonEmpty && rounds < MaxRounds) {
      val round = pending.reverse
      pending = Nil
      round.foreach { m =>
        m.queued = false
        m.read()
      }
      // laid out once they are all set, not once for each: a column of lines measured together was laid out again for
      // every line
      everyui.layout.LayoutPass.batch(round.foreach(_.set()))
      rounds += 1
    }
    scheduled = false
    if (pending.nonEmpty) {
      scheduled = true
      Platform.beforePaint(() => flush())
    }
  }
}
