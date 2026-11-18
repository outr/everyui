package everyui.chart

import everyui.Color

/** One line of a legend: a series' `name`, the `colour` it is drawn in, the shape its swatch has, and any `detail`
  * written after the name, quieter (a slice's value and share). */
case class LegendEntry(name: String, colour: Color, key: LegendKey = LegendKey.Box, detail: String = "")
