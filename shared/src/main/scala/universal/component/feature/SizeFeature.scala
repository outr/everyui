package universal.component.feature

import reactify.*
import universal.component.Component
import universal.component.types.SizeProperty

/** youi's `SizeFeature`. Like youi's (which is constructed with `measured.width` and `measured.height`), it brings
  * the component's measured size into being with the component, so a measure that happens before anything reads
  * the size (adding the component to a container) is not missed. */
class SizeFeature(component: Component):
  private val measuredWidth: Val[Double] = component.measured.width
  private val measuredHeight: Val[Double] = component.measured.height

  lazy val width: SizeProperty = new SizeProperty(measuredWidth, d => component.node.setWidth(d))
  lazy val height: SizeProperty = new SizeProperty(measuredHeight, d => component.node.setHeight(d))

  lazy val center: Val[Double] = Val(width / 2.0)
  lazy val middle: Val[Double] = Val(height / 2.0)
