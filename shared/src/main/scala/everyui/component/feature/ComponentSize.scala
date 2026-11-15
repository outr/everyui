package everyui.component.feature

import reactify.Val
import everyui.component.Component
import everyui.component.types.SizeProperty
import everyui.render.Platform

/** A component's width (`across`) or height (`SizeFeature`): written to its node, and its width's changes told to it
  * (`Component.widthHeard`), by the size itself rather than by a function of each attached to it. On the web, a size
  * that follows the measured one, asked for out of the page and not read since, is read as it is read
  * (MeasuredFeature.peek). */
private[feature] class ComponentSize(component: Component, measured: Val[Double], across: Boolean)
  extends SizeProperty(measured, ComponentSize.unwritten) {
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

  /** A component's size, made, on the web, following the measured one as it is, without reading it. */
  def apply(component: Component, measured: Val[Double], across: Boolean): SizeProperty =
    if (!Platform.measuresInBatches) new ComponentSize(component, measured, across)
    else MeasuredFeature.quietly(new ComponentSize(component, measured, across))
}
