package everyui.table

import everyui.list.ListRow
import reactify.*

/** A row of a `VirtualTable`: a cell for each column, placed along the row by the columns' widths and the table's
  * sideways scroll (from the right, right to left). */
class TableRow[T](table: VirtualTable[T]) extends ListRow[T] {
  val cells: Vector[GridCell[T]] = table.columns.map { column =>
    val cell = column.cell()
    cell.item := item()
    cell.selected := selected()
    cell.size.width := table.widthOf(column.key)
    cell.size.height := TableRow.this.size.height()
    cell.visible := table.widthOf(column.key) > 0.0
    cell.position.left := ListRow.along(table.startOf(column.key) - table.scrollX(), cell.size.width(),
      TableRow.this.size.width())
    cell.size.width.attach(_ => cell.node.setClip(cell.size.width(), cell.size.height()))
    cell.size.height.attach(_ => cell.node.setClip(cell.size.width(), cell.size.height()))
    cell
  }
  children ++= cells

  override def kind: String = "TableRow"
}
