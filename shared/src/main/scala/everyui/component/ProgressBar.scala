package everyui.component

import everyui.Color
import everyui.component.types.{Plain, SemanticRange, Semantics}
import everyui.theme.Theme
import reactify.*

/** A track filled to `fraction` (0..1): `width` wide to start with, and the fill follows `size.width` after. A
  * screen reader hears it as a progress bar, at the part done as a percentage. */
class ProgressBar(width: Double, height: Double = 6.0) extends Container {
  val fraction: Var[Double] = own(Plain[Double](0.0))
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
  semantics := {
    val percent = math.round(math.max(0.0, math.min(1.0, fraction())) * 100.0).toDouble
    Semantics("progressbar", range = Some(SemanticRange(percent, 0.0, 100.0, percent.toInt.toString + "%")))
  }
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
