package everyui.component.feature

import everyui.component.Component
import everyui.component.types.SizeProperty
import everyui.render.Platform

/** A component's width (`across`) or height (`SizeFeature`): written to its node, its width's changes told to it
  * (`Component.widthHeard`) by the size itself rather than by a function of each attached to it, and told the size the
  * component measures as it is read (`MeasuredFeature.set`), from the size last read as it is made. On the web, a size
  * that follows the measured one, asked for out of the page and not read since, is read as it is read
  * (MeasuredFeature.peek). */
private[feature] class ComponentSize(component: Component, across: Boolean)
  extends SizeProperty(null, ComponentSize.unwritten) {
  start(if (across) component.measured.setWidth else component.measured.setHeight)

  /** The size the component measures, as it is read. */
  private[feature] def measuredAs(d: Double): Unit = follow(d)

  override protected def writeTo(d: Double): Unit =
    if (across) component.node.setWidth(d) else component.node.setHeight(d)

  override protected def changed(): Unit = if (across) component.widthHeard()

  override def get: Double = {
    val set = super.get
    if (!Platform.measuresInBatches || explicit) set else component.measured.peek(across, set)
  }
}

object ComponentSize {
  /** Nothing: a component's size writes itself (`writeTo`). */
  val unwritten: Double => Unit = _ => ()
}
