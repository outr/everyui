package everyui.component

import everyui.Color
import everyui.component.types.FontWeight
import reactify.*

/** A word in a coloured pill. */
class Chip(label: String, colour: Color) extends Container {
  private val text = new TextView {
    content @= label
    font.size @= 12.0
    font.weight @= FontWeight.Bold
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
