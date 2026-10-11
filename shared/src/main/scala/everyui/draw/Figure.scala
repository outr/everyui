package everyui.draw

import everyui.Color

/** A path as it is drawn: filled with `fill`, and its outline `stroke`d `width` pixels wide, either or both. Bind a
  * drawing's figures to the theme (`stroke = Some(Theme.accent())`) and they follow the palette. `evenOdd` fills by
  * SVG's even-odd rule, where a shape inside another is a hole whichever way it runs, rather than by the non-zero rule
  * every figure is filled by otherwise (a platform that draws no paths has no rules to tell apart). */
case class Figure(path: Path, fill: Option[Color] = None, stroke: Option[Color] = None, width: Double = 1.0,
                  evenOdd: Boolean = false)
