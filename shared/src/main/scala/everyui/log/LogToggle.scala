package everyui.log

import everyui.Color
import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A small button that is on or off (`on`, both ways), with what it does as its tooltip: a glyph, or a few letters
  * where they say it better ("Aa", ".*"). On, it wears the accent. */
class LogToggle(on: Var[Boolean], tip: String, glyph: MaterialIcon = MaterialIcon.Empty, letters: String = "")
  extends Container {
  private val lit: Val[Boolean] = own(Val(focused() || event.pointer.over()))
  private val ink: Val[Color] = own(Val(if (on()) Theme.accent() else if (lit()) Theme.text() else Theme.muted()))
  private val mark: Component =
    if (letters.isEmpty) new MaterialIconView(glyph, 20.0) {
      color := ink()
      position.left @= 6.0
      position.top @= 6.0
    }
    else new TextView {
      content @= letters
      font.family @= LogView.face
      font.size @= 13.0
      font.weight @= FontWeight.Bold
      color := ink()
      position.left := (32.0 - size.width()) / 2.0
      position.top := (32.0 - size.height()) / 2.0
    }

  focusable @= true
  radius @= 8.0
  tooltip @= tip
  size.width @= 32.0
  size.height @= 32.0
  backgroundColor := (if (on()) Theme.accent().withAlpha(0.2) else if (lit()) Theme.controlLit() else Color.Clear)
  semantics := Semantics.switch(tip, on())
  tapSlop @= true
  children += mark
  event.click.attach { e =>
    e.consume()
    on @= !on()
  }

  override def kind: String = "LogToggle(" + tip + ")"
}
