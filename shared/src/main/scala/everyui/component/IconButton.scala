package everyui.component

import everyui.Color
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A glyph that does something, with what it does as its tooltip. */
class IconButton(glyph: MaterialIcon, tip: String, action: () => Unit = () => (), colour: Color = Theme.muted()) extends Container {
  val icon: MaterialIconView = new MaterialIconView(glyph, 22.0) {
    position.left @= 5.0
    position.top @= 5.0
  }
  focusable @= true
  radius @= 16.0
  tooltip @= tip
  size.width @= 32.0
  size.height @= 32.0
  // its own hover, lighter than the card it may sit on, and the glyph brightens with it
  backgroundColor := (if (focused() || event.pointer.over()) Theme.controlLit() else Color.Clear)
  icon.color := (if (focused() || event.pointer.over()) Theme.text() else colour)
  children += icon
  event.click.attach { e =>
    e.consume()
    action()
  }
}
