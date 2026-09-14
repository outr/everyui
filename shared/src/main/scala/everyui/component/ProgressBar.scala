package everyui.component

import everyui.Color
import everyui.theme.Theme
import reactify.*

/** A track filled to `fraction` (0..1). */
class ProgressBar(width: Double, height: Double = 6.0) extends Container {
  val fraction: Var[Double] = Var(0.0)
  val fill: Var[Color] = Var(Theme.accent())

  private val done = new Container {
    size.width := width * math.max(0.0, math.min(1.0, fraction()))
    size.height @= height
    backgroundColor := fill()
    radius @= height / 2.0
  }
  size.width @= width
  size.height @= height
  backgroundColor := Theme.controlLit()
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
