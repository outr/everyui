package everyui.chart

/** The mark a pointer is on, a press landed on, or the keys moved to: its `category` (a bar's, a point's, a slice's
  * name, a cell's), its `series` (empty for a chart of one), its `value`, and where each is in the chart's data
  * (`categoryIndex`, `seriesIndex`). `pressed` is a press or a click rather than a pointer passing over. */
case class Pointed(category: String, series: String, value: Double, categoryIndex: Int, seriesIndex: Int,
                   pressed: Boolean = false)
