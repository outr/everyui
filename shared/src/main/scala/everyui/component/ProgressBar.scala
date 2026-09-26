package everyui.component

import everyui.Color
import everyui.theme.Theme
import reactify.*

/** A track filled to `fraction` (0..1): `width` wide to start with, and the fill follows `size.width` after. */
class ProgressBar(width: Double, height: Double = 6.0) extends Container {
  val fraction: Var[Double] = own(Var(0.0))
  val fill: Var[Color] = own(Var(Theme.accent()))
  /** What shows where the bar is not filled. */
  val track: Var[Color] = own(Var(Theme.controlLit()))

  private val done = new Container {
    size.width := ProgressBar.this.size.width() * math.max(0.0, math.min(1.0, fraction()))
    size.height @= height
    backgroundColor := fill()
    radius @= height / 2.0
  }
  size.width @= width
  size.height @= height
  backgroundColor := track()
  radius @= height / 2.0
  children += done
}

object ProgressBar {
  def apply(fraction: Double, width: Double, height: Double = 6.0, fill: Color = Theme.accent()): ProgressBar = {
    val bar = new ProgressBar(width, height)
    bar.fraction @= fraction
    bar.fill @= fill
    bar
  }
}
