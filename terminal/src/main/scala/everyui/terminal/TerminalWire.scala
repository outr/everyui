package everyui.terminal

import everyui.Color
import everyui.component.EmbedGuest

/** The text the terminal bridge carries (terminal/assets/bridge/terminal_bridge.js): a message is its name, the
  * terminal's id and a payload, joined by the unit separator (U+001F), the payload last and taken whole. Options and
  * themes go as JSON, which the guest reads; nothing the app hears is JSON. */
object TerminalWire {
  val separator: Char = '\u001f'

  /** A message for the guest. */
  def message(name: String, id: String, payload: String = ""): String =
    name + separator + id + separator + payload

  /** A message from the guest as its name, id and payload, if it is one. */
  def parse(text: String): Option[(String, String, String)] = {
    val first = text.indexOf(separator)
    val second = if (first < 0) -1 else text.indexOf(separator, first + 1)
    if (second < 0) None
    else Some((text.substring(0, first), text.substring(first + 1, second), text.substring(second + 1)))
  }

  /** A grid as the guest says it, "cols,rows". */
  def size(payload: String): Option[TerminalSize] = {
    val comma = payload.indexOf(',')
    if (comma < 0) None
    else payload.substring(0, comma).toIntOption.flatMap(c => payload.substring(comma + 1).toIntOption.map(r => TerminalSize(c, r)))
  }

  /** A JSON object of fields already written as JSON. */
  def obj(fields: (String, String)*): String =
    fields.map(f => EmbedGuest.quote(f._1) + ":" + f._2).mkString("{", ",", "}")

  def str(s: String): String = EmbedGuest.quote(s)
  def num(d: Double): String = if (d == math.floor(d) && !d.isInfinite) d.toLong.toString else d.toString
  def bool(b: Boolean): String = if (b) "true" else "false"
  def color(c: Color): String = str(if (c.alphaInt == 255) c.toHex else c.toRGBA)

  private val ansiNames = Vector("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white", "brightBlack",
    "brightRed", "brightGreen", "brightYellow", "brightBlue", "brightMagenta", "brightCyan", "brightWhite")

  /** A theme as xterm.js takes it (`ITheme`). */
  def theme(t: TerminalTheme): String = obj((List(
    "foreground" -> color(t.foreground), "background" -> color(t.background), "cursor" -> color(t.cursor),
    "cursorAccent" -> color(t.cursorAccent), "selectionBackground" -> color(t.selection),
    "selectionInactiveBackground" -> color(t.selection.withAlpha(t.selection.alpha * 0.6))) ++
    ansiNames.indices.filter(_ < t.ansi.size).map(i => ansiNames(i) -> color(t.ansi(i))))*)

  /** Chords as the guest matches them: the key's name lowercased ("space" for the space bar), and the modifiers as
    * this platform resolves them. */
  def chords(chords: List[(String, Boolean, Boolean, Boolean, Boolean)]): String =
    chords.map { case (key, ctrl, meta, alt, shift) =>
      obj("key" -> str(if (key == " ") "space" else key.toLowerCase), "ctrl" -> bool(ctrl), "meta" -> bool(meta),
        "alt" -> bool(alt), "shift" -> bool(shift))
    }.mkString("[", ",", "]")
}
