package everyui.component.feature

import reactify.*
import everyui.component.Component
import everyui.component.types.SizeProperty

/** youi's `SizeFeature`. Like youi's (which is constructed with `measured.width` and `measured.height`), it brings
  * the component's measured size into being with the component, so a measure that happens before anything reads
  * the size (adding the component to a container) is not missed. */
class SizeFeature(component: Component) {
  private val measuredWidth: Val[Double] = component.measured.width
  private val measuredHeight: Val[Double] = component.measured.height

  // whether each size has been made: one never made has nothing bound to unbind as the component is taken apart
  private var widthMade = false
  private var heightMade = false
  lazy val width: SizeProperty = {
    widthMade = true
    ComponentSize(component, measuredWidth, across = true)
  }
  lazy val height: SizeProperty = {
    heightMade = true
    ComponentSize(component, measuredHeight, across = false)
  }

  /** Each size made unbound from what it follows (`Component.dispose`), without making one that was never made. */
  private[everyui] def unbind(): Unit = {
    if (widthMade) Component.unbind(width)
    if (heightMade) Component.unbind(height)
  }

  lazy val center: Val[Double] = Val(width / 2.0)
  lazy val middle: Val[Double] = Val(height / 2.0)
}
