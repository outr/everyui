package everyui.component

import everyui.Color
import everyui.component.types.Semantics
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A glyph that does something, with what it does as its tooltip. While `busy` (its work under way: a queue running)
  * the glyph breathes (`MaterialIconView.busy`), or, where less motion is asked for, stands still in the accent; its
  * tooltip and its name to a screen reader say it is busy. */
class IconButton(glyph: MaterialIcon, tip: String, action: () => Unit = () => (), colour: Option[Color] = None) extends Container {
  /** The glyph's colour at rest: the theme's muted colour, following the theme, unless `colour` names one. */
  val tint: Var[Color] = own(Var(Theme.muted()))
  colour.foreach(c => tint @= c)
  val icon: MaterialIconView = new MaterialIconView(glyph, 22.0) {
    position.left @= 5.0
    position.top @= 5.0
    // still and busy, it is marked by the accent, at full strength to be seen
    override protected def stillStrength: Double = 1.0
  }
  /** At work: the glyph breathes until it is set back. */
  val busy: Var[Boolean] = icon.busy
  focusable @= true
  radius @= 16.0
  private def said: String = if (busy()) tip + ", " + everyui.i18n.Words.busy else tip
  tooltip := said
  // a glyph says nothing to a screen reader: what it does does
  semantics := Semantics.button(said)
  size.width @= 32.0
  size.height @= 32.0
  // its own hover, lighter than the card it may sit on, and the glyph brightens with it
  backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else Color.Clear)
  icon.color := (if (busy() && everyui.motion.ReduceMotion()) Theme.accent()
    else if (focusShown() || event.pointer.over()) Theme.text() else tint())
  children += icon
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    action()
  }
}
