package everyui.color

import everyui.Color
import everyui.component.Container
import everyui.component.types.Semantics
import everyui.theme.Theme

/** A colour to choose with one press: a square `side` pixels across in `colour` (over checks if it is see-through),
  * ringed while it has the focus or is `chosen`. A click, a tap or OK calls `choose`. */
class ColorSwatch(val colour: Color, chosen: => Boolean, side: Double = ColorSwatch.side)(choose: Color => Unit)
  extends Container {
  private val fill = new Container

  focusable @= true
  size.width @= side
  size.height @= side
  radius @= 6.0
  borderWidth := (if (focusShown()) 2.5 else if (chosen) 2.0 else 1.0)
  borderColor := (if (focusShown() || chosen) Theme.accent() else Theme.border())
  semantics @= Semantics.button(ColorText.hex(colour, alpha = true))
  tooltip @= ColorText.hex(colour, alpha = true)
  if (colour.alphaInt < 255) {
    val checks = new ColorChecker(side - 8.0, side - 8.0, 4.0)
    checks.position.left @= 4.0
    checks.position.top @= 4.0
    children += checks
  }
  fill.position.left @= 4.0
  fill.position.top @= 4.0
  fill.size.width @= side - 8.0
  fill.size.height @= side - 8.0
  fill.radius @= 3.0
  fill.backgroundColor @= colour
  children += fill
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    choose(colour)
  }

  override def kind: String = "ColorSwatch(" + colour.toString + ")"
}

object ColorSwatch {
  val side: Double = 30.0
}
