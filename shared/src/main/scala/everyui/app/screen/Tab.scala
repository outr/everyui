package everyui.app.screen

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.theme.Theme
import reactify.*

/** A screen's name in an app bar: the shown one is lit; a click, a tap or Enter shows it. */
class Tab(label: String, screen: Screen, manager: ScreenManager) extends Container {
  private val text = new TextView {
    content @= label
    font.size @= 14.0
    color := (if (manager.active().exists(_ eq screen)) Theme.accent() else Theme.text())
    position.left @= 12.0
    position.top @= 6.0
  }
  focusable @= true
  radius @= 6.0
  size.width := text.size.width + 24.0
  size.height := text.size.height + 12.0
  backgroundColor := (if (focused() || event.pointer.over()) Theme.raised() else Color.Clear)
  children += text
  event.click.attach(_ => manager.show(screen).startUnit())
}
