package everyui.component

import everyui.Color
import everyui.component.types.WhiteSpace

/** A boxed message `width` wide, tinted in `tone` (an error in the theme's danger, a notice in its accent). It wraps,
  * grows to fit, and follows what it reads. */
class Notice(text: => String, tone: => Color, width: Double) extends Container {
  private val words = new TextView {
    whiteSpace @= WhiteSpace.Normal
    size.width @= width - 24.0
    font.size @= 14.0
    color := tone
    position.left @= 12.0
    position.top @= 12.0
    content := text
  }
  size.width @= width
  size.height := words.size.height() + 24.0
  radius @= 8.0
  backgroundColor := tone.withAlpha(0.12)
  borderWidth @= 1.0
  borderColor := tone.withAlpha(0.5)
  children += words
}
