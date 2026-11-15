package everyui.call

import everyui.component.types.Semantics
import everyui.component.{Container, MaterialIconView}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A round button that opens the call's settings (`open`: `CallSettings.open` with the call or the preview), drawn as
  * a [[MediaToggle]] is so it sits beside them on a call's bar or a "ready to join?" page: a gear, lit under the
  * pointer and ringed in the accent while focused. A click, a tap, or Enter on it opens them; its tooltip and its name
  * to a screen reader are `CallWords.settings`.
  *
  * {{{
  *   bar.children += new CallSettingsButton(() => CallSettings.open(Some(call)))
  *   page.children += new CallSettingsButton(() => CallSettings.open(preview))
  * }}} */
class CallSettingsButton(open: () => Unit, diameter: Double = 48.0) extends Container {
  private val lit: Val[Boolean] = own(Val(focusShown() || event.pointer.over()))
  private val mark = new MaterialIconView(MaterialIcon.Settings, 24.0)
  mark.color := Theme.text()
  mark.position.center := diameter / 2.0
  mark.position.middle := diameter / 2.0

  focusable @= true
  tapSlop @= true
  tooltip @= CallWords.settings
  size.width @= diameter
  size.height @= diameter
  radius @= diameter / 2.0
  backgroundColor := (if (lit()) Theme.controlLit() else Theme.raised())
  borderWidth := (if (focusShown()) 2.5 else 0.0)
  borderColor := Theme.accent()
  semantics := Semantics.button(CallWords.settings)
  children += mark
  event.click.attach { e =>
    e.consume()
    open()
  }

  override def kind: String = "CallSettingsButton"
}
