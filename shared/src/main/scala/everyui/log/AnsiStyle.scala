package everyui.log

/** How a stretch of a log line is drawn, as a terminal's SGR codes left it: its colours and its attributes. A colour
  * is a number: `AnsiStyle.Default` (the view's own), 0 to 255 (the sixteen ANSI colours, then xterm's 6x6x6 cube and
  * its greys), or a colour of its own (`AnsiStyle.rgb`, from `38;2;r;g;b`). */
case class AnsiStyle(fg: Int = AnsiStyle.Default,
                     bg: Int = AnsiStyle.Default,
                     bold: Boolean = false,
                     dim: Boolean = false,
                     italic: Boolean = false,
                     underline: Boolean = false,
                     strike: Boolean = false,
                     reverse: Boolean = false,
                     conceal: Boolean = false) {
  /** Whether it is the view's own style, nothing set. */
  def plain: Boolean = this == AnsiStyle.Plain
}

object AnsiStyle {
  /** The view's own colour. */
  val Default: Int = -1
  /** Where the colours of their own start: `rgb` is this plus its red, green and blue. */
  val OwnColour: Int = 16777216
  val Plain: AnsiStyle = AnsiStyle()

  /** A colour of its own, `r`, `g` and `b` 0 to 255. */
  def rgb(r: Int, g: Int, b: Int): Int =
    OwnColour + math.max(0, math.min(255, r)) * 65536 + math.max(0, math.min(255, g)) * 256 + math.max(0, math.min(255, b))

  /** Whether `colour` is one of its own rather than one of the 256. */
  def isRgb(colour: Int): Boolean = colour >= OwnColour
}
