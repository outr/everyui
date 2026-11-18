package everyui.chart

import everyui.component.{Container, Row, TextView}
import everyui.component.types.Semantics
import everyui.layout.{FlowLayout, VerticalLayout}
import everyui.theme.Theme
import reactify.*

/** The names of a chart's series, each after a swatch of its colour: in a row that wraps onto more as it runs out of
  * room (`size.width`), or one under another (`column`). It is what tells the series apart for everybody, colour or
  * no colour, so its words are in the text's own colours, never the series'. A chart of two series or more shows one
  * of its own; one stands alone beside charts that share it.
  *
  * {{{
  *   val legend = Legend(Vector(LegendEntry("Work", Theme.series(0)), LegendEntry("Personal", Theme.series(1))))
  * }}} */
class Legend extends Container {
  val entries: Var[Vector[LegendEntry]] = own(Var(Vector.empty))
  /** One entry under another, rather than along a row. */
  val column: Var[Boolean] = own(Var(false))

  layout := Some(if (column()) VerticalLayout(6.0) else FlowLayout(14.0, 6.0))
  size.height := contentHeight()
  semantics @= Semantics.list
  children.bind(entries().toList)(item)

  private def item(e: LegendEntry): Container = {
    val swatch = new Container {
      e.key match {
        case LegendKey.Box =>
          size.width @= 10.0
          size.height @= 10.0
          radius @= 2.0
        case LegendKey.Line =>
          size.width @= 14.0
          size.height @= 3.0
          radius @= 1.5
        case LegendKey.Dot =>
          size.width @= 8.0
          size.height @= 8.0
          radius @= 4.0
      }
      backgroundColor @= e.colour
    }
    val name = Chart.words(TextView(e.name, 12.0, if (e.detail.isEmpty) Theme.muted() else Theme.text()))
    val row = if (e.detail.isEmpty) Row(6.0, middle = true)(swatch, name)
      else Row(6.0, middle = true)(swatch, name, Chart.words(TextView(e.detail, 12.0, Theme.muted())))
    row.semantics @= Semantics.listItem
    row
  }
}

object Legend {
  def apply(entries: => Vector[LegendEntry], column: Boolean = false): Legend = {
    val legend = new Legend
    legend.entries := entries
    legend.column @= column
    legend
  }
}
