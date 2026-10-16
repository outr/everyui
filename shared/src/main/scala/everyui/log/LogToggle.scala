package everyui.log

import everyui.component.ToggleButton
import everyui.font.MaterialIcon
import reactify.*

/** A log toolbar's `ToggleButton`: on or off (`on`, both ways), with what it does as its tooltip, a glyph or a few
  * letters ("Aa", ".*"). */
class LogToggle(on: Var[Boolean], tip: String, glyph: MaterialIcon = MaterialIcon.Empty, letters: String = "")
  extends ToggleButton(on, tip, glyph, letters) {
  override def kind: String = "LogToggle(" + tip + ")"
}
