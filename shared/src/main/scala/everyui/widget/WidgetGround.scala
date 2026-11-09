package everyui.widget

import everyui.Color

/** A tinted ground behind a row or a column, so it reads as a card: its colour by its role in the theme (light and
  * dark, as the device is set), or an exact one in both; how opaque it is, so the wallpaper shows through a translucent
  * widget; its corners' radius; and the room inside it around the parts. An exact colour's own alpha is multiplied
  * into `opacity`.
  *
  * Android draws the corners from a fixed few shapes (`WidgetGround.radii`), the nearest to `radius`. */
case class WidgetGround(colour: WidgetColour = WidgetColour.Surface,
                        opacity: Double = 1.0,
                        radius: Double = 12.0,
                        padding: Double = 8.0,
                        exact: Option[Color] = None) {
  /** How opaque it is drawn, 0 to 255: `opacity`, held to 0..1, times an exact colour's alpha. */
  def alpha: Int = WidgetGround.alpha(opacity * exact.fold(1.0)(_.alpha))
}

object WidgetGround {
  /** The corners' radii a ground is drawn with, in points: square, and three rounded. */
  val radii: Vector[Int] = Vector(0, 8, 12, 16)

  /** The radius in `radii` nearest `points`. */
  def radius(points: Double): Int = radii.minBy(r => math.abs(r - points))

  /** `opacity`, held to 0..1, as an alpha from 0 to 255 (and fully opaque where it is not a number). */
  def alpha(opacity: Double): Int =
    if (opacity.isNaN) 255 else math.round(math.max(0.0, math.min(1.0, opacity)) * 255.0).toInt
}
