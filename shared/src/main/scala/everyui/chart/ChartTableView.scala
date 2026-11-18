package everyui.chart

import everyui.component.{Container, ScrollContainer, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.kit.Mirror
import everyui.theme.Theme
import reactify.*

/** A chart's data as a table (`ChartTable`), in a box that scrolls where it is taller than the room it is given: a
  * header row, then a row for each category, each column as wide as its widest cell, the values lined up at their
  * end. A screen reader reads it as a table, and words selected across it copy as one. */
class ChartTableView(data: ChartTable) extends ScrollContainer {
  private val rowHeight = 26.0
  private val cells: Vector[Vector[TextView]] = (data.header +: data.rows).zipWithIndex.map { case (row, r) =>
    row.zipWithIndex.map { case (words, c) =>
      val t = Chart.words(TextView(words, 12.5, if (r == 0) Theme.muted() else Theme.text()))
      if (r == 0) t.font.weight @= FontWeight.SemiBold
      t.semantics @= Semantics(if (r == 0) "columnheader" else if (c == 0) "rowheader" else "cell")
      t.copyAs @= (if (r == 0) "th" else "td")
      t
    }
  }
  private val columns = data.header.size
  /** Each column's width: its widest cell and a gap. */
  private val widths: Val[Vector[Double]] = own(Val((0 until columns).toVector.map(c =>
    cells.map(row => if (c < row.size) row(c).size.width() else 0.0).max + 18.0)))
  private val grid = new Container {
    size.width := widths().sum
    size.height @= cells.size * rowHeight
    semantics @= Semantics("table")
    copyAs @= "table"
  }

  cells.zipWithIndex.foreach { case (row, r) =>
    val line = new Container {
      size.width := grid.size.width()
      size.height @= rowHeight
      position.top @= r * rowHeight
      semantics @= Semantics("row")
      copyAs @= "tr"
    }
    // a hairline under the header
    if (r == 0) line.children += new Container {
      size.width := grid.size.width()
      size.height @= 1.0
      position.top @= rowHeight - 1.0
      backgroundColor := Theme.border()
    }
    row.zipWithIndex.foreach { case (cell, c) =>
      // the first column at its start, the values at their column's end, as numbers line up
      Mirror.place(cell, () => grid.size.width(), () => {
        val before = widths().take(c).sum
        if (c == 0) before else before + widths()(c) - 18.0 - cell.size.width()
      })
      cell.position.top := (rowHeight - cell.size.height()) / 2.0
      line.children += cell
    }
    grid.children += line
  }
  // at the start of the room, the right in a right-to-left language
  Mirror.place(grid, () => math.max(size.width(), grid.size.width()), () => 0.0)
  content.children += grid
}
