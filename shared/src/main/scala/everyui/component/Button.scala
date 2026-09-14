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

