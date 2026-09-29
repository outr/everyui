package everyui.color

import everyui.Color

/** A colour as the words a person types and reads: hex (`#1e88e5`, `#1e88e580` with alpha, `1e8` short) and the
  * channels as whole numbers. The parsers are strict: anything that is not a colour is None, never a guess. */
object ColorText {
  private val digits: String = "0123456789abcdef"

  /** `#rrggbb`, or `#rrggbbaa` with `alpha` (then only while it is not opaque, unless `always`). */
  def hex(color: Color, alpha: Boolean, always: Boolean = false): String =
    if (alpha && (always || color.alphaInt < 255)) color.toHexAlpha else color.toHex

  /** A hex colour: 3, 4, 6 or 8 digits, with or without `#`, in either case, spaces around it ignored. */
  def parseHex(text: String): Option[Color] = {
    val t = text.trim.toLowerCase
    val v = if (t.startsWith("#")) t.substring(1) else t
    val n = v.length
    var ok = n == 3 || n == 4 || n == 6 || n == 8
    var i = 0
    while (ok && i < n) {
      if (digits.indexOf(v.charAt(i).toString) < 0) ok = false
      i += 1
    }
    if (!ok) None
    else if (n == 3 || n == 4) {
      val r = digit(v, 0) * 17
      val g = digit(v, 1) * 17
      val b = digit(v, 2) * 17
      val a = if (n == 4) digit(v, 3) * 17 else 255
      Some(new Color(r, g, b, a))
    }
    else {
      val a = if (n == 8) digit(v, 6) * 16 + digit(v, 7) else 255
      Some(new Color(digit(v, 0) * 16 + digit(v, 1), digit(v, 2) * 16 + digit(v, 3), digit(v, 4) * 16 + digit(v, 5), a))
    }
  }

  /** A channel, 0 to 255, as a whole number; spaces around it ignored. */
  def parseChannel(text: String): Option[Int] = {
    val t = text.trim
    var ok = t.nonEmpty && t.length <= 3
    var value = 0
    var i = 0
    while (ok && i < t.length) {
      val d = "0123456789".indexOf(t.charAt(i).toString)
      if (d < 0) ok = false else value = value * 10 + d
      i += 1
    }
    if (ok && value <= 255) Some(value) else None
  }

  /** `fraction` (0 to 1) as a whole percentage: `42%`. */
  def percent(fraction: Double): String = math.round(Hsv.unit(fraction) * 100.0).toInt.toString + "%"

  /** `hue` (0 to 1) in whole degrees: `210°`. */
  def degrees(hue: Double): String = (math.round(Hsv.unit(hue) * 360.0).toInt % 360).toString + "°"

  private def digit(v: String, at: Int): Int = digits.indexOf(v.charAt(at).toString)
}
