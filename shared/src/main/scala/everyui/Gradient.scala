package everyui

/** A linear gradient: `angle` degrees as CSS has it (0 runs bottom to top, 90 left to right), through its `stops`; or,
  * given a `radial` shape, a radial one, its first stop at the centre and its last on the circle (`Gradient.radial`).
  * The web, the desktop and Android draw both; a backend that draws none (a Roku, an Apple TV) fills with the first
  * stop. */
case class Gradient(angle: Double, stops: List[GradientStop], radial: Option[RadialShape] = None) {
  /** As the seam takes it: `"#rrggbbaa at,#rrggbbaa at"`. */
  def encoded: String = stops.map(s => s.color.toHexAlpha + " " + s.at.toString).mkString(",")

  /** The colour `position` of the way along it (0 to 1), straight between the stops either side, as it is drawn. */
  def at(position: Double): Color = {
    val before = stops.filter(_.at <= position)
    val after = stops.filter(_.at >= position)
    (before.lastOption, after.headOption) match {
      case (Some(a), Some(b)) =>
        if (b.at - a.at <= 0.0) a.color else a.color.blend(b.color, (position - a.at) / (b.at - a.at))
      case (Some(a), None) => a.color
      case (None, Some(b)) => b.color
      case (None, None) => Color.Clear
    }
  }

  /** The same gradient with nothing showing: its colours, all transparent. What a wash fades in from, or out to. */
  def clear: Gradient = copy(stops = stops.map(s => GradientStop(s.color.withAlpha(0.0), s.at)))
}

object Gradient {
  /** `colors` spread evenly along `angle`. */
  def apply(angle: Double, colors: Color*): Gradient = {
    val last = math.max(1, colors.size - 1).toDouble
    new Gradient(angle, colors.toList.zipWithIndex.map { case (c, i) => GradientStop(c, i / last) })
  }

  /** A radial gradient centred at `x`, `y` (fractions of the box), reaching `radius` of its shorter side, through
    * `stops` from the centre out (`RadialShape`):
    * {{{
    * Gradient.radial(0.2, 0.1, 0.9, List(GradientStop(Color.fromHex("#7c4dff").withAlpha(0.35), 0.0),
    *   GradientStop(Color.Clear, 1.0)))
    * }}} */
  def radial(x: Double, y: Double, radius: Double, stops: List[GradientStop]): Gradient =
    new Gradient(0.0, stops, Some(RadialShape(x, y, radius)))

  /** Part of the way from `a` to `b` (0 is `a`, 1 is `b`): the stops at `b`'s places, each coloured part of the way
    * from what `a` is there to what `b` is, so two gradients with their stops in different places still move into
    * each other smoothly. Either may be missing, and is then the other with nothing showing — a wash that one side
    * has and the other does not fades rather than jumping. The angle and the shape cannot be blended, and turn half-way. */
  def lerp(a: Option[Gradient], b: Option[Gradient], t: Double): Option[Gradient] = (a, b) match {
    case (None, None) => None
    case _ =>
      val from = a.getOrElse(b.map(_.clear).getOrElse(Gradient(0.0, Nil)))
      val to = b.getOrElse(from.clear)
      Some(Gradient(if (t < 0.5) from.angle else to.angle,
        to.stops.map(s => GradientStop(from.at(s.at).blend(s.color, t), s.at)), if (t < 0.5) from.radial else to.radial))
  }

  /** The seam's encoding back as `(hex, at)` pairs, for a backend to draw from. */
  def decode(encoded: String): List[(String, Double)] =
    if (encoded.isEmpty) Nil
    else encoded.split(",").toList.map { stop =>
      val space = stop.indexOf(' ')
      (stop.substring(0, space), stop.substring(space + 1).toDouble)
    }
}
