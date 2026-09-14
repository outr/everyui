package everyui.component

import everyui.theme.Theme
import reactify.*

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
