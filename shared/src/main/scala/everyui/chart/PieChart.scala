package everyui.chart

/** A `DonutChart` with no hole: the same slices, legend and tips, without the total in the middle.
  *
  * {{{
  *   val split = PieChart(Vector("Work", "Personal"), Vector(312.0, 88.0))
  * }}} */
object PieChart {
  def apply(names: => Vector[String], values: => Vector[Double],
            format: Double => String = ChartFormat.compact): DonutChart = DonutChart(names, values, format, hole = 0.0)
}
