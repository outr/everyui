package everyui.chart

/** A `LineChart` with the space under each line filled faintly (`area`): best for a single series over time, where
  * the filled shape reads as an amount; for several series that cross, their lines alone read better.
  *
  * {{{
  *   val spend = AreaChart.time(days(), Vector(Series("Spend", amounts())), ChartFormat.money("$"))
  * }}} */
object AreaChart {
  def apply(categories: => Vector[String], series: => Vector[Series],
            format: Double => String = ChartFormat.compact): LineChart =
    LineChart(categories, series, format, area = true)

  def time(times: => Vector[Long], series: => Vector[Series], format: Double => String = ChartFormat.compact): LineChart =
    LineChart.time(times, series, format, area = true)
}
