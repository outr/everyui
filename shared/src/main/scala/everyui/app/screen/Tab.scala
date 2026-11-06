package everyui.app.screen

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.theme.Theme
import everyui.component.types.Semantics
import reactify.*

/** A screen's name in an app bar: the shown one is lit; a click, a tap or Enter shows it. */
class Tab(label: String, screen: Screen, manager: ScreenManager) extends Container {
  private val text = new TextView {
    content @= label
    font.size @= 14.0
    color := (if (manager.active().exists(_ eq screen)) Theme.accentText() else Theme.text())
    position.left @= 12.0
    position.top @= 6.0
  }
  focusable @= true
  radius @= 6.0
  // a tab, chosen while its screen shows
  semantics := Semantics("tab", label = label, selected = Some(manager.active().exists(_ eq screen)))
  size.width := text.size.width + 24.0
  size.height := text.size.height + 12.0
  backgroundColor := (if (focusShown() || event.pointer.over()) Theme.raised() else Color.Clear)
  children += text
  event.click.attach(_ => manager.show(screen).startUnit())
}
