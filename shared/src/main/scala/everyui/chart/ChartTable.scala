package everyui.chart

/** A chart's data as a table, for whoever cannot read it from the drawing (WCAG's guidance for charts): its `header`
  * (what each column is) and its `rows`, each a category's name and its values as the chart writes them. A chart shows
  * it in place of its drawing on asking (`Chart.table`), and a screen reader reads it as a table. */
case class ChartTable(header: Vector[String], rows: Vector[Vector[String]])

object ChartTable {
  /** `categories` down the side and `series` across, each value written by `format`, under a first column headed
    * `heading`. */
  def of(heading: String, categories: Vector[String], series: Vector[Series], format: Double => String): ChartTable =
    ChartTable(heading +: series.map(_.name),
      categories.indices.toVector.map(i => categories(i) +: series.map(s => format(Chart.valueAt(s, i)))))
}
