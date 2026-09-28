package everyui.draw

import everyui.Color

/** A path as it is drawn: filled with `fill`, and its outline `stroke`d `width` pixels wide, either or both. Bind a
  * drawing's figures to the theme (`stroke = Some(Theme.accent())`) and they follow the palette. */
case class Figure(path: Path, fill: Option[Color] = None, stroke: Option[Color] = None, width: Double = 1.0)
