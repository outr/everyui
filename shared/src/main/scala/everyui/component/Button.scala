package everyui.component

import everyui.Color
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A pill that does something: the pointer over it or the focus on it brightens it; a click, a tap or Enter runs
  * `action` (and `event.click`). The primary one wears the accent. */
class Button(name: String, action: () => Unit = () => (), primary: Boolean = false) extends Container {
  /** What it says; bind it to change it. */
  val label: Var[String] = Var(name)
  private val text = new TextView {
    content := label()
    font.size @= 13.0
    color := (if (primary) Theme.onAccent() else Theme.text())
    position.left @= 14.0
    position.top @= 6.0
  }

  focusable @= true
  radius @= 14.0
  size.width := text.size.width + 28.0
  size.height := text.size.height + 12.0
  backgroundColor := {
    val lit = focused() || event.pointer.over()
    if (primary) (if (lit) Theme.accentLit() else Theme.accent()) else (if (lit) Theme.controlLit() else Theme.control())
  }
  children += text
  event.click.attach { e =>
    e.consume()
    action()
  }
}

/** A glyph that does something, with what it does as its tooltip. */
class IconButton(glyph: MaterialIcon, tip: String, action: () => Unit = () => (), colour: => Color = Theme.muted()) extends Container {
  val icon: MaterialIconView = new MaterialIconView(glyph, 22.0) {
    color := colour
    position.left @= 5.0
    position.top @= 5.0
  }
  focusable @= true
  radius @= 16.0
  tooltip @= tip
  size.width @= 32.0
  size.height @= 32.0
  backgroundColor := (if (focused() || event.pointer.over()) Theme.raised() else Color.Clear)
  children += icon
  event.click.attach { e =>
    e.consume()
    action()
  }
}

/** One of a set of choices: the chosen one wears the accent, the rest are muted; a click, a tap or Enter chooses it. */
class Choice(label: String, chosen: Boolean, choose: () => Unit) extends Container {
  private val text = new TextView {
    content @= label
    font.size @= 12.0
    color := (if (chosen) Theme.onAccent() else Theme.text())
    position.left @= 10.0
    position.top @= 4.0
  }
  focusable @= true
  radius @= 11.0
  size.width := text.size.width + 20.0
  size.height := text.size.height + 8.0
  backgroundColor := (if (chosen) Theme.accent() else if (focused() || event.pointer.over()) Theme.controlLit() else Theme.control())
  children += text
  event.click.attach { e =>
    e.consume()
    choose()
  }
}

/** A word in a coloured pill. */
class Chip(label: String, colour: Color) extends Container {
  private val text = new TextView {
    content @= label
    font.size @= 12.0
    font.weight @= everyui.component.types.FontWeight.Bold
    color @= Color.White
    position.left @= 10.0
    position.top @= 4.0
  }
  backgroundColor @= colour
  radius @= 12.0
  size.width := text.size.width + 20.0
  size.height := text.size.height + 8.0
  children += text
}
