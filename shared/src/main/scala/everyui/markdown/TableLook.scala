package everyui.markdown

import everyui.Color
import everyui.component.Container
import everyui.render.Gestures
import everyui.theme.Theme

/** How a table of words is drawn (every Markdown table, and a `DataTable`): its header on a band of its own (`header`)
  * in words of `headerWeight`, every second row under it tinted (`zebra`, in `stripe`), its edge a thin line in the
  * theme's border colour with corners rounded by `radius`, the header and the rows cut to them, and rules between the
  * cells only where `rules` says. A cell's words stand `across` in from its sides and `down` from its top and bottom
  * (`headerDown` in the header).
  *
  * The colours are read from the theme as the table is drawn, so they follow a change of palette; by default they are
  * tints (the accent's for the header, the text's for the stripes) rather than solid colours, so a picture or a bubble
  * behind the table shows through as it does round it. A palette's `extras` can name them: "table.header" and
  * "table.stripe".
  *
  * {{{
  *   MarkdownView.tableLook @= TableLook(zebra = false, radius = 4.0)
  *   new DataTable(columns, cells, DataTableStyle(look = TableLook.ruled))
  * }}} */
case class TableLook(zebra: Boolean = true,
                     radius: Double = 8.0,
                     header: () => Color = TableLook.band,
                     stripe: () => Color = TableLook.tint,
                     rules: Boolean = false,
                     across: Double = 12.0,
                     down: Double = 8.0,
                     headerDown: Double = 10.0,
                     headerWeight: Int = 600) {
  /** The fill of the row shown `place`th under the header (0 the first): the stripe on every second one, the first
    * left clear. */
  def rowFill(place: Int): Color = if (zebra && place % 2 == 1) stripe() else Color.Clear

  /** How far a row's words stand from its top and bottom: the header's or a body row's. */
  def vertical(header: Boolean): Double = if (header) headerDown else down
}

object TableLook {
  /** The header's band: the palette's "table.header", else the accent faintly. */
  val band: () => Color = () => Theme.extra("table.header", Theme.accent().withAlpha(0.1))
  /** A stripe: the palette's "table.stripe", else the text more faintly still. */
  val tint: () => Color = () => Theme.extra("table.stripe", Theme.text().withAlpha(0.04))
  /** The raised colour, the header's in `ruled`. */
  val raised: () => Color = () => Theme.raised()

  /** As a table was drawn before this look: square, every cell ruled, the header on the raised colour in bold, no
    * stripes, 8 pixels round each cell's words. */
  lazy val ruled: TableLook = TableLook(zebra = false, radius = 0.0, header = raised, rules = true, across = 8.0,
    down = 8.0, headerDown = 8.0, headerWeight = 700)

  /** A table's edge, drawn over its header and rows: a thin line in the border colour round its box, rounded as `look`
    * says. A click, a finger and the wheel go through it. */
  private[everyui] def edge(look: TableLook): Container = new Container {
    borderWidth @= 1.0
    borderColor := Theme.border()
    radius @= look.radius
    Gestures.passThrough(node)
  }

  /** `grid`, a table's header and rows, cut to its rounded corners, and `edge` drawn round it. */
  private[everyui] def clip(grid: Container, edge: Container): Unit = {
    edge.size.width := grid.size.width()
    edge.size.height := grid.size.height()
    if (grid.radius() > 0.0) {
      grid.size.width.attach(_ => grid.node.setClip(grid.size.width(), grid.size.height()))
      grid.size.height.attach(_ => grid.node.setClip(grid.size.width(), grid.size.height()))
      grid.node.setClip(grid.size.width(), grid.size.height())
    }
  }
}
