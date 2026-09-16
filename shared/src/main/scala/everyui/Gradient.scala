package everyui

/** A linear gradient: `angle` degrees as CSS has it (0 runs bottom to top, 90 left to right), through its `stops`.
  * A backend that draws none (a Roku) fills with the first stop. */
case class Gradient(angle: Double, stops: List[GradientStop]) {
  /** As the seam takes it: `"#rrggbbaa at,#rrggbbaa at"`. */
  def encoded: String = stops.map(s => s.color.toHexAlpha + " " + s.at.toString).mkString(",")
}

object Gradient {
  /** `colors` spread evenly along `angle`. */
  def apply(angle: Double, colors: Color*): Gradient = {
    val last = math.max(1, colors.size - 1).toDouble
    new Gradient(angle, colors.toList.zipWithIndex.map { case (c, i) => GradientStop(c, i / last) })
  }

  /** The seam's encoding back as `(hex, at)` pairs, for a backend to draw from. */
  def decode(encoded: String): List[(String, Double)] =
    if (encoded.isEmpty) Nil
    else encoded.split(",").toList.map { stop =>
      val space = stop.indexOf(' ')
      (stop.substring(0, space), stop.substring(space + 1).toDouble)
    }
}
