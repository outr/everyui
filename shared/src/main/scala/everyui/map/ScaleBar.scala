package everyui.map

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.draw.{Drawing, Figure, Path}
import everyui.theme.Theme

/** A map's scale: a bar as long as a round distance on the ground is at the middle of the view, and the distance over
  * it, in metres and kilometres or in feet and miles. */
private[map] class ScaleBar extends Container {
  private val bar = new Drawing
  private val words = TextView("", 11.0, Theme.text())
  private val most = 100.0

  size.width @= most + 8.0
  size.height @= 26.0
  radius @= 3.0
  backgroundColor := Theme.surface().withAlpha(0.75)
  words.position.left @= 4.0
  words.position.top @= 1.0
  bar.position.left @= 4.0
  bar.position.top @= 17.0
  bar.size.height @= 6.0
  children ++= List(bar, words)

  /** Shown for a view where a pixel is `metersPerPixel` of the ground. */
  def show(metersPerPixel: Double, imperial: Boolean): Unit = {
    val step = ScaleBar.pick(metersPerPixel, most, imperial)
    val w = math.floor(step.pixels + 0.5)
    words.content @= step.label
    bar.size.width @= w + 1.0
    bar.figures @= List(Figure(Path.line(0.5, 0.0, 0.5, 5.5).lineTo(w + 0.5, 5.5).lineTo(w + 0.5, 0.0),
      stroke = Some(Theme.text()), width = 1.0))
    size.width @= math.max(w + 8.0, 48.0)
  }
}

private[map] object ScaleBar {
  private val feetPerMeter = 3.28084
  private val feetPerMile = 5280.0

  /** The longest round distance (1, 2 or 5 times a power of ten) no more than `most` pixels long where a pixel is
    * `metersPerPixel` of the ground: in metres, or kilometres from 1 km; or in feet, or miles from a mile. */
  def pick(metersPerPixel: Double, most: Double, imperial: Boolean): ScaleStep =
    if (imperial) {
      val feet = metersPerPixel * most * feetPerMeter
      if (feet < feetPerMile) {
        val n = round(feet)
        ScaleStep(n / feetPerMeter / metersPerPixel, whole(n) + " ft")
      }
      else {
        val n = round(feet / feetPerMile)
        ScaleStep(n * feetPerMile / feetPerMeter / metersPerPixel, whole(n) + " mi")
      }
    }
    else {
      val meters = metersPerPixel * most
      if (meters < 1000.0) {
        val n = round(meters)
        ScaleStep(n / metersPerPixel, whole(n) + " m")
      }
      else {
        val n = round(meters / 1000.0)
        ScaleStep(n * 1000.0 / metersPerPixel, whole(n) + " km")
      }
    }

  /** The largest of 1, 2 and 5 times a power of ten that is no more than `d` (and 1 for anything under it). */
  def round(d: Double): Double =
    if (d < 1.0) 1.0
    else {
      var power = 1.0
      while (power * 10.0 <= d) power = power * 10.0
      if (power * 5.0 <= d) power * 5.0 else if (power * 2.0 <= d) power * 2.0 else power
    }

  // a whole number with its thousands marked
  private def whole(d: Double): String = {
    val digits = d.toLong.toString
    var out = ""
    var i = 0
    while (i < digits.length) {
      if (i > 0 && (digits.length - i) % 3 == 0) out = out + ","
      out = out + digits.substring(i, i + 1)
      i += 1
    }
    out
  }
}
