package everyui.draw

import everyui.Color
import everyui.theme.Theme

/** A small line of `values` across a box, highest at the top, with room for the stroke at the edges: a trend at a
  * glance. With `area`, the space under the line is filled faintly in the same colour. The line follows the values,
  * the box's size and the colour, which can be the theme's.
  *
  * {{{
  *   val trend = Sparkline(stats().map(_.count.toDouble))
  *   trend.size.width @= 80.0
  *   trend.size.height @= 20.0
  * }}} */
object Sparkline {
  def apply(values: => Seq[Double], color: => Color = Theme.accent(), width: Double = 1.5,
            area: Boolean = false): Drawing = {
    val drawing = new Drawing
    drawing.figures := figures(values, drawing.size.width(), drawing.size.height(), color, width, area)
    drawing
  }

  /** The figures of a sparkline `across` by `down`. */
  def figures(values: Seq[Double], across: Double, down: Double, color: Color, width: Double = 1.5,
              area: Boolean = false): List[Figure] =
    if (values.size < 2 || across <= 0.0 || down <= 0.0) Nil
    else {
      val low = values.min
      val span = math.max(values.max - low, 1e-9)
      val pad = width / 2.0
      val points = values.zipWithIndex.map { case (v, i) =>
        (pad + (across - width) * i / (values.size - 1), pad + (down - width) * (1.0 - (v - low) / span))
      }
      val line = Figure(Path.polyline(points), stroke = Some(color), width = width)
      if (!area) List(line)
      else {
        val under = Path.polyline(points).lineTo(points.last._1, down).lineTo(points.head._1, down).close
        List(Figure(under, fill = Some(color.withAlpha(color.alpha * 0.2))), line)
      }
    }
}
