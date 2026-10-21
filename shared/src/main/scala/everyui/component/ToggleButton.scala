package everyui.component

import everyui.Color
import everyui.component.types.{FontWeight, Semantics, TextDirection}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A small button that is on or off (`on`, both ways), named `name`, which is its tooltip too: a glyph, or a few
  * letters where they say it better ("Aa", ".*"). On, it wears the accent. A screen reader hears a toggle button and
  * whether it is pressed. A click, a tap, or Enter on it turns it over. */
class ToggleButton(on: Var[Boolean], name: String, glyph: MaterialIcon = MaterialIcon.Empty, letters: String = "")
  extends Container {
  private val lit: Val[Boolean] = own(Val(focusShown() || event.pointer.over()))
  private val ink: Val[Color] = own(Val(if (on()) Theme.accent() else if (lit()) Theme.text() else Theme.muted()))
  private val mark: Component =
    if (letters.isEmpty) new MaterialIconView(glyph, 20.0) {
      color := ink()
      position.left @= 6.0
      position.top @= 6.0
    }
    else new TextView {
      content @= letters
      font.family @= "Roboto Mono"
      font.size @= 13.0
      font.weight @= FontWeight.Bold
      // the letters as they are written, ".*" never turned to "*." in a right-to-left language
      direction @= TextDirection.LeftToRight
      color := ink()
      position.left := (ToggleButton.side - size.width()) / 2.0
      position.top := (ToggleButton.side - size.height()) / 2.0
    }

  focusable @= true
  radius @= 8.0
  tooltip @= name
  size.width @= ToggleButton.side
  size.height @= ToggleButton.side
  backgroundColor := (if (on()) Theme.accent().withAlpha(0.2) else if (lit()) Theme.controlLit() else Color.Clear)
  semantics := Semantics.toggle(name, on())
  tapSlop @= true
  children += mark
  event.click.attach { e =>
    e.consume()
    on @= !on()
  }

  override def kind: String = "ToggleButton(" + name + ")"
}

object ToggleButton {
  /** Its square's side. */
  val side: Double = 32.0
}
