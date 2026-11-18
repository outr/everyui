package everyui.chart

import everyui.i18n.{Icu, Words}

/** What the charts say, in the language in use (Words' eleven languages): an app gives its own wording or another
  * language with `Words.add` under these keys ("chart.bar", …). */
object ChartWords {
  def bar: String = Words("chart.bar")
  def line: String = Words("chart.line")
  def area: String = Words("chart.area")
  def donut: String = Words("chart.donut")
  def pie: String = Words("chart.pie")
  def heatmap: String = Words("chart.heatmap")
  def gauge: String = Words("chart.gauge")
  /** The table's first column, over the categories. */
  def category: String = Words("chart.category")
  /** What the smaller slices are folded into. */
  def other: String = Words("chart.other")
  def total: String = Words("chart.total")
  /** A table's column of values, and of shares of the whole. */
  def value: String = Words("chart.value")
  def share: String = Words("chart.share")
  /** The toggle's tip, and what a screen reader hears of it, as the chart shows. */
  def showTable: String = Words("chart.showTable")
  /** The same as the table shows. */
  def showChart: String = Words("chart.showChart")
  /** "12 values, from 0 to 48". */
  def range(n: Int, low: String, high: String): String =
    Icu.format(Words("chart.range"), Map("n" -> n.toString, "low" -> low, "high" -> high))
  /** "highest Wed, 12". */
  def highest(category: String, value: String): String =
    Words("chart.highest").replace("{category}", category).replace("{value}", value)
  /** "62 of 100". */
  def of(value: String, max: String): String = Words("chart.of").replace("{value}", value).replace("{max}", max)
  /** "up 12%", "down 3". */
  def up(delta: String): String = Words("chart.up").replace("{delta}", delta)
  def down(delta: String): String = Words("chart.down").replace("{delta}", delta)
  def noData: String = Words("chart.noData")
}
