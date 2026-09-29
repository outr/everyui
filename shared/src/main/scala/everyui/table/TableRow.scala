package everyui.table

import everyui.Color
import everyui.component.Container
import everyui.list.{DropPosition, ListRow}
import everyui.render.Platform
import reactify.*

/** A row of a `VirtualTable`: a cell for each column, placed along the row by the columns' widths and the table's
  * sideways scroll (from the right, right to left). Its cells follow the table's columns: a column added gets a cell,
  * one taken away takes its cell with it, and the rest keep theirs. While its row has not come from a paged source,
  * a grey bar stands in each cell. */
class TableRow[T](table: VirtualTable[T]) extends ListRow[T] {
  private var made: Vector[(GridColumn[T], GridCell[T], Container)] = Vector.empty
  private val hovered: Val[Boolean] = if (Platform.pointerSupport()) event.pointer.over else own(Val(false))
  private val wash = new Container {
    backgroundColor := table.hoverColor()
    visible := hovered() && item().nonEmpty
  }
  wash.size.width := size.width()
  wash.size.height := size.height()

  /** The cells, in the order the columns show. */
  def cells: Vector[GridCell[T]] = made.map(_._2)

  private def build(columns: Vector[GridColumn[T]]): Unit = {
    val before = made
    made = columns.zipWithIndex.map { case (column, at) =>
      before.find(_._1 == column).getOrElse((column, cell(column), placeholder(column, at)))
    }
    val gone = before.filterNot(m => made.exists(_._1 == m._1))
    if (gone.nonEmpty || made != before) {
      children @= Vector[everyui.component.Component](wash) ++ made.map(_._2) ++ made.map(_._3)
      gone.foreach { case (_, c, bar) =>
        c.dispose()
        bar.dispose()
      }
    }
  }

  private def cell(column: GridColumn[T]): GridCell[T] = {
    val key = column.key
    val cell = column.cell()
    cell.item := item()
    cell.selected := selected()
    cell.size.width := table.widthOf(key)
    cell.size.height := TableRow.this.size.height()
    cell.visible := table.widthOf(key) > 0.0 && (item().nonEmpty || key == VirtualTable.checkKey)
    cell.position.left := ListRow.along(table.startOf(key) - table.scrollX(), cell.size.width(),
      TableRow.this.size.width())
    cell.size.width.attach(_ => cell.node.setClip(cell.size.width(), cell.size.height()))
    cell.size.height.attach(_ => cell.node.setClip(cell.size.width(), cell.size.height()))
    cell
  }

  // the grey bar standing in a cell while its row has not come: of a length of its own for each row and column
  private def placeholder(column: GridColumn[T], at: Int): Container = {
    val key = column.key
    new Container {
      private val room: Val[Double] = own(Val(math.max(0.0, table.widthOf(key) - ListRow.inset * 2.0)))
      size.height @= 10.0
      radius @= 5.0
      size.width := room() * (0.45 + ((index() * 7 + at * 13) % 5) * 0.1)
      backgroundColor := everyui.theme.Theme.muted().withAlpha(0.18)
      visible := item().isEmpty && room() > 8.0 && key != VirtualTable.checkKey
      position.top := (TableRow.this.size.height() - 10.0) / 2.0
      position.left := ListRow.along(table.startOf(key) - table.scrollX() + ListRow.inset, size.width(),
        TableRow.this.size.width())
    }
  }

  backgroundColor := {
    val fill = ListRow.fill(selected(), current(), active(), drop().contains(DropPosition.Into))
    if (fill != Color.Clear) fill else item().flatMap(t => table.rowColor()(t)).getOrElse(Color.Clear)
  }
  children += wash
  build(table.shown())
  follow(table.shown, (c: Vector[GridColumn[T]]) => build(c))

  override def kind: String = "TableRow"
}
