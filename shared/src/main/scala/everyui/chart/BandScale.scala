package everyui.chart

/** `count` categories side by side along pixels from `start` to `end`, each in a band of its own (`step` wide), its
  * marks in the middle `width` of it and the rest air (`padding`, a share of the step). `end` may be less than
  * `start`, as in a right-to-left language the first category is at the right. */
case class BandScale(count: Int, start: Double, end: Double, padding: Double = 0.3) {
  /** How far apart the bands are, in pixels (never negative). */
  def step: Double = if (count <= 0) 0.0 else math.abs(end - start) / count

  /** How wide the middle of a band, its marks' room, is. */
  def width: Double = step * (1.0 - padding)

  /** The middle of band `i`. */
  def center(i: Int): Double = {
    val sign = if (end >= start) 1.0 else -1.0
    start + sign * (i + 0.5) * step
  }

  /** The band at pixel `p`, None where it is outside every band. */
  def at(p: Double): Option[Int] =
    if (count <= 0 || step == 0.0) None
    else {
      val along = if (end >= start) p - start else start - p
      val i = math.floor(along / step).toInt
      if (along < 0.0 || i >= count) None else Some(i)
    }
}
