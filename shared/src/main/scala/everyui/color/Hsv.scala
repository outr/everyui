package everyui.color

import everyui.Color

/** A colour as a picker holds it: hue, saturation and value (brightness), and alpha, each 0 to 1. Held apart from the
  * `Color` it makes, since eight bits a channel lose what a picker must keep: the hue of a grey, the saturation of
  * black, the exact place a drag left the thumb.
  *
  * {{{
  *   val teal = Hsv(0.5, 0.6, 0.7).color
  *   val back = Hsv.of(teal)                // within a channel's rounding of where it started
  * }}} */
case class Hsv(hue: Double, saturation: Double, value: Double, alpha: Double = 1.0) {
  /** The colour it is, its alpha kept. */
  def color: Color = Color.fromHSB(Hsv.unit(hue), Hsv.unit(saturation), Hsv.unit(value)).withAlpha(Hsv.unit(alpha))

  /** The colour it is, opaque unless `withAlpha`. */
  def color(withAlpha: Boolean): Color = if (withAlpha) color else copy(alpha = 1.0).color

  /** The pure hue it has: full saturation and value, opaque. */
  def pure: Color = Color.fromHSB(Hsv.unit(hue), 1.0, 1.0)
}

object Hsv {
  /** `color` as hue, saturation and value. What it cannot say is taken from `previous`: a grey has no hue, black has
    * no saturation either, so a picker dragged down to black and back up comes back to the hue it left. */
  def of(color: Color, previous: Hsv = Hsv(0.0, 0.0, 0.0)): Hsv = {
    val v = color.brightness
    if (v == 0.0) Hsv(previous.hue, previous.saturation, 0.0, color.alpha)
    else {
      val s = color.saturation
      if (s == 0.0) Hsv(previous.hue, 0.0, v, color.alpha)
      else Hsv(color.hue, s, v, color.alpha)
    }
  }

  /** `x` kept between 0 and 1. */
  def unit(x: Double): Double = if (x < 0.0) 0.0 else if (x > 1.0) 1.0 else x
}
