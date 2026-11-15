package everyui.component.feature

import reactify.*
import everyui.component.Component
import everyui.component.types.SizeProperty

/** youi's `SizeFeature`. Like youi's (which is constructed with `measured.width` and `measured.height`), it brings
  * the component's measured size into being with the component, so a measure that happens before anything reads
  * the size (adding the component to a container) is not missed. Each size is told what the component measures as
  * that is read (`measuredAs`), from the first read on. */
class SizeFeature(component: Component) {
  component.measured

  // each size, once made: one never made has nothing to be told, nor to unbind as the component is taken apart
  private var widthSize: ComponentSize = null
  private var heightSize: ComponentSize = null
  lazy val width: SizeProperty = {
    widthSize = new ComponentSize(component, across = true)
    widthSize
  }
  lazy val height: SizeProperty = {
    heightSize = new ComponentSize(component, across = false)
    heightSize
  }

  /** The component measured `d` wide (`across`) or tall: each size made that follows what it measures takes it. */
  private[feature] def measuredAs(across: Boolean, d: Double): Unit =
    if (across) { if (widthSize != null) widthSize.measuredAs(d) }
    else if (heightSize != null) heightSize.measuredAs(d)

  /** Each size made unbound from what it follows (`Component.dispose`), without making one that was never made. */
  private[everyui] def unbind(): Unit = {
    if (widthSize != null) Component.unbind(width)
    if (heightSize != null) Component.unbind(height)
  }

  lazy val center: Val[Double] = Val(width / 2.0)
  lazy val middle: Val[Double] = Val(height / 2.0)
}
