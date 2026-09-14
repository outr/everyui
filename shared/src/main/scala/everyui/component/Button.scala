package everyui.component

import everyui.Color
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A pill that does something: the pointer over it or the focus on it lights it; a click, a tap or Enter runs
  * `action` (and `event.click`). `style` says how much it stands out, `glyph` puts an icon before the words, and
  * `points` sizes the words, the padding following. The focus on it shows plainly: a filled one takes the text colour,
  * the rest a ring in the accent. A disabled one is muted and does nothing, but stays put. */
class Button(name: String, action: () => Unit = () => (), style: ButtonStyle = ButtonStyle.Tonal,
             glyph: Option[MaterialIcon] = None, points: Double = 13.0) extends Container {
  /** What it says; bind it to change it. */
  val label: Var[String] = Var(name)
  val enabled: Var[Boolean] = Var(true)
  /** At least this wide, its content centred: a button as wide as the form it ends. */
  val minWidth: Var[Double] = Var(0.0)

  // The focus is unmistakable from across a room: a filled button turns to the text colour, anything else gets a ring
  // in the accent over the lit fill. The pointer resting on it only lights it.
  private val lit: Val[Boolean] = Val(enabled() && (focused() || event.pointer.over()))
  private val ringed: Val[Boolean] = Val(focused() && style != ButtonStyle.Filled)
  private val foreground: Val[Color] = Val(
    if (!enabled()) Theme.muted()
    else if (style == ButtonStyle.Filled) Theme.onAccent()
    else if (style == ButtonStyle.Tonal) Theme.text()
    else if (lit() && style == ButtonStyle.Text) Theme.text()
    else Theme.accent())
  private val words: TextView = new TextView {
    content := label()
    font.size @= points
    color := foreground()
  }
  private val icon: Option[MaterialIconView] = glyph.map(g => new MaterialIconView(g, points * 1.4))
  icon.foreach(i => i.color := foreground())
  private val line: Container = Row(points * 0.6, middle = true)((icon.toList :+ words)*)

  focusable @= true
  size.width := math.max(minWidth(), line.size.width() + points * 2.2)
  size.height := line.size.height() + points
  radius := size.height() / 2.0
  line.position.center := size.center()
  line.position.middle := size.middle()
  backgroundColor := {
    if (style == ButtonStyle.Filled)
      (if (!enabled()) Theme.control() else if (focused()) Theme.text() else if (lit()) Theme.accentLit() else Theme.accent())
    else if (style == ButtonStyle.Tonal) (if (lit()) Theme.controlLit() else Theme.control())
    else if (lit()) Theme.controlLit()
    else Color.Clear
  }
  borderWidth := (if (ringed()) 2.5 else if (style == ButtonStyle.Outlined) 1.0 else 0.0)
  borderColor := (if (ringed() || (lit() && style == ButtonStyle.Outlined)) Theme.accent() else Theme.border())
  children += line
  event.click.attach { e =>
    e.consume()
    if (enabled()) action()
  }
}
