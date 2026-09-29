package everyui.color

import everyui.Color
import everyui.component.Container

/** Where a picker's slider or area is set: a white ring, `diameter` across, round the colour it points at, which
  * shows on light and dark alike. Drawn as a disc in a disc, which every platform draws, a Roku included. */
class ColorThumb(fill: => Color, diameter: Double = ColorThumb.diameter) extends Container {
  private val inner = new Container
  size.width @= diameter
  size.height @= diameter
  radius @= diameter / 2.0
  backgroundColor @= Color.White
  shadow @= Some(everyui.Shadow(0.0, 1.0, 3.0, Color.Black.withAlpha(0.45)))
  inner.size.width @= diameter - 6.0
  inner.size.height @= diameter - 6.0
  inner.position.left @= 3.0
  inner.position.top @= 3.0
  inner.radius @= (diameter - 6.0) / 2.0
  inner.backgroundColor := fill.withAlpha(1.0)
  children += inner

  override def kind: String = "ColorThumb"
}

object ColorThumb {
  val diameter: Double = 18.0
}
