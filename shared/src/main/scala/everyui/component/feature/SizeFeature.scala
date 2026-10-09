package everyui.component.feature

import reactify.*
import everyui.component.Component
import everyui.component.types.SizeProperty
import everyui.render.Platform

/** youi's `SizeFeature`. Like youi's (which is constructed with `measured.width` and `measured.height`), it brings
  * the component's measured size into being with the component, so a measure that happens before anything reads
  * the size (adding the component to a container) is not missed. */
class SizeFeature(component: Component) {
  private val measuredWidth: Val[Double] = component.measured.width
  private val measuredHeight: Val[Double] = component.measured.height

  lazy val width: SizeProperty = SizeFeature.size(component, measuredWidth, across = true,
    d => component.node.setWidth(d))
  lazy val height: SizeProperty = SizeFeature.size(component, measuredHeight, across = false,
    d => component.node.setHeight(d))

  lazy val center: Val[Double] = Val(width / 2.0)
  lazy val middle: Val[Double] = Val(height / 2.0)
}

object SizeFeature {
  // on the web, a size that follows the measured one, asked for out of the page and not read since, is read as it is
  // read (MeasuredFeature.peek); made, it follows the measured size as it is, without reading it
  private def size(component: Component, measured: Val[Double], across: Boolean, write: Double => Unit): SizeProperty =
    if (!Platform.measuresInBatches) new SizeProperty(measured, write)
    else MeasuredFeature.quietly(new SizeProperty(measured, write) {
      override def get: Double = {
        val set = super.get
        if (explicit) set else component.measured.peek(across, set)
      }
    })
}
