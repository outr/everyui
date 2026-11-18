package everyui.chart

/** One series of a chart: its `name` (in the legend, the tips and the table) and its `values`, one for each of the
  * chart's categories in order (a missing one is 0), drawn in `tone`: by default the palette's series colour for its
  * place, and a colour follows its series, so give a series its own slot (`ChartTone.Slot`) where the series shown
  * can change and each should keep its colour. */
case class Series(name: String, values: Vector[Double], tone: ChartTone = ChartTone.Auto)
