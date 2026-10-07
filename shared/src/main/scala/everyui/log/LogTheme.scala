package everyui.log

import everyui.Color
import everyui.theme.Palette

/** A log view's colours: its words and ground, the quieter colour of its line numbers and times, the sixteen ANSI
  * colours a program names (black, red, green, yellow, blue, magenta, cyan and white, then their bright forms), and
  * the marks of a search's matches (`hit`, and `current` for the one it is on).
  *
  * Made `readable`, as `LogTheme.of` makes it, a colour a program names that would not read on the ground (black on a
  * dark one, white or yellow on a light one, any of the 256 or a colour of its own as well) is taken towards the
  * words' colour until it does; `LogTheme.of(palette, raw = true)` draws xterm's own colours as they are.
  *
  * {{{
  *   log.theme @= LogTheme.of(Palette.light)            // or leave it following Theme.palette, as it starts
  *   log.theme @= LogTheme.of(Theme.palette(), raw = true)
  * }}} */
case class LogTheme(foreground: Color, background: Color, muted: Color, ansi: Vector[Color], hit: Color,
                    current: Color, readable: Boolean = true) {
  private val light: Boolean = LogTheme.luminance(background) > 0.25

  /** Colour `code` of the 256 (see `AnsiStyle`), or a colour of its own, as this theme draws it. */
  def colour(code: Int): Color =
    if (AnsiStyle.isRgb(code)) {
      val c = code - AnsiStyle.OwnColour
      Color.fromRGBA(c / 65536, (c / 256) % 256, c % 256, 1.0)
    }
    else if (code < 16) ansi(math.max(0, code))
    else if (code < 232) {
      val i = code - 16
      Color.fromRGBA(LogTheme.step(i / 36), LogTheme.step((i / 6) % 6), LogTheme.step(i % 6), 1.0)
    }
    else {
      val grey = 8 + (math.min(255, code) - 232) * 10
      Color.fromRGBA(grey, grey, grey, 1.0)
    }

  /** The colour of words in `style`: reversed, the ground's; dimmed, half way to the ground; concealed, none. */
  def letters(style: AnsiStyle): Color = {
    val base =
      if (style.reverse) (if (style.bg == AnsiStyle.Default) background else colour(style.bg))
      else if (style.fg == AnsiStyle.Default) foreground
      else if (readable) legible(colour(style.fg))
      else colour(style.fg)
    if (style.conceal) Color.Clear else if (style.dim) base.blend(background, 0.45) else base
  }

  /** What is drawn behind words in `style`, if anything: its ground, or reversed, its words' colour. */
  def ground(style: AnsiStyle): Option[Color] =
    if (style.reverse) Some(if (style.fg == AnsiStyle.Default) foreground else colour(style.fg))
    else if (style.bg == AnsiStyle.Default) None
    else Some(colour(style.bg))

  /** `c`, taken towards the words' colour until it reads on the ground (a contrast of 4.5 to 1). */
  def legible(c: Color): Color = {
    val ground = LogTheme.luminance(background)
    var out = c
    var t = 0.0
    while (LogTheme.contrast(LogTheme.luminance(out), ground) < 4.5 && t < 1.0) {
      t += 0.1
      out = c.blend(if (light) Color.Black else Color.White, t)
    }
    out
  }
}

object LogTheme {
  /** The sixteen on a dark ground. */
  lazy val darkAnsi: Vector[Color] = Vector("000000", "cd3131", "0dbc79", "e5e510", "2472c8", "bc3fbc", "11a8cd",
    "e5e5e5", "666666", "f14c4c", "23d18b", "f5f543", "3b8eea", "d670d6", "29b8db", "ffffff").map(Color.fromHex)

  /** The sixteen on a light ground: darker, so yellow and white still read. */
  lazy val lightAnsi: Vector[Color] = Vector("000000", "cd3131", "00bc00", "949800", "0451a5", "bc05bc", "0598bc",
    "555555", "666666", "cd3131", "14ce14", "b5ba00", "0451a5", "bc05bc", "0598bc", "a5a5a5").map(Color.fromHex)

  /** xterm's own sixteen. */
  lazy val xterm: Vector[Color] = Vector("000000", "cd0000", "00cd00", "cdcd00", "0000ee", "cd00cd", "00cdcd",
    "e5e5e5", "7f7f7f", "ff0000", "00ff00", "ffff00", "5c5cff", "ff00ff", "00ffff", "ffffff").map(Color.fromHex)

  /** The theme for an EveryUI palette: its text on its surface, line numbers in its muted colour, matches marked in
    * its warning colour, and the sixteen for a dark or a light ground, made to read on it; with `raw`, xterm's, as
    * they are. */
  def of(palette: Palette, raw: Boolean = false): LogTheme = {
    val ground = palette.surface
    val sixteen = if (raw) xterm else if (luminance(ground) > 0.25) lightAnsi else darkAnsi
    LogTheme(palette.text, ground, palette.muted, sixteen, palette.warning.withAlpha(0.3),
      palette.warning.withAlpha(0.7), readable = !raw)
  }

  // a step of xterm's 6x6x6 cube
  private def step(v: Int): Int = if (v == 0) 0 else 55 + v * 40

  /** How bright `c` looks, 0 to 1 (its channels squared, near enough to sRGB's curve). */
  def luminance(c: Color): Double = 0.2126 * c.red * c.red + 0.7152 * c.green * c.green + 0.0722 * c.blue * c.blue

  /** The contrast between two luminances, 1 to 21. */
  def contrast(a: Double, b: Double): Double = (math.max(a, b) + 0.05) / (math.min(a, b) + 0.05)
}
