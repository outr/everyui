package everyui.terminal

import everyui.Color
import everyui.theme.Palette

/** A terminal's colours: its text and ground, the cursor (and the text under a block cursor), the selection, and the
  * sixteen ANSI colours a program names (`ansi`: black, red, green, yellow, blue, magenta, cyan and white, then their
  * bright forms, in that order).
  *
  * {{{
  *   term.theme @= TerminalTheme.of(Palette.light)            // or leave it following Theme.palette, as it starts
  *   term.theme @= TerminalTheme.of(Theme.palette()).copy(background = Color.Black)
  * }}} */
case class TerminalTheme(foreground: Color, background: Color, cursor: Color, cursorAccent: Color, selection: Color,
                         ansi: Vector[Color])

object TerminalTheme {
  /** The ANSI colours on a dark ground. */
  lazy val darkAnsi: Vector[Color] = Vector("000000", "cd3131", "0dbc79", "e5e510", "2472c8", "bc3fbc", "11a8cd",
    "e5e5e5", "666666", "f14c4c", "23d18b", "f5f543", "3b8eea", "d670d6", "29b8db", "ffffff").map(Color.fromHex)

  /** The ANSI colours on a light ground: darker, so yellow and white still read. */
  lazy val lightAnsi: Vector[Color] = Vector("000000", "cd3131", "00bc00", "949800", "0451a5", "bc05bc", "0598bc",
    "555555", "666666", "cd3131", "14ce14", "b5ba00", "0451a5", "bc05bc", "0598bc", "a5a5a5").map(Color.fromHex)

  /** The theme for an EveryUI palette: its text on its surface, the cursor in its accent, and the ANSI colours for a
    * dark or a light ground, by how bright the surface is. */
  def of(palette: Palette): TerminalTheme = {
    val ground = palette.surface
    val light = 0.2126 * ground.red + 0.7152 * ground.green + 0.0722 * ground.blue > 0.5
    TerminalTheme(palette.text, ground, palette.accent, ground, palette.accent.withAlpha(0.35),
      if (light) lightAnsi else darkAnsi)
  }
}
