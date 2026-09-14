package everyui.component

import everyui.Color
import everyui.layout.VerticalLayout
import everyui.theme.Theme
import reactify.*

/** A column: its title, its width, and the cell for a row. */
case class Column[T](title: String, width: Double, cell: T => Component)

/** Rows of `T` under a header, each column at its width. A row brightens under the pointer or the focus and
  * reports a click. */
class Table[T](columns: List[Column[T]], onRow: T => Unit = (_: T) => ()) extends Container {
  val rows: Var[List[T]] = Var(Nil)

  private val header = new Container {
    size.height @= 36.0
    children ++= place(columns.map(c => TextView(c.title, 12.0, Theme.muted())))
  }
  private val body = new Container {
    layout @= Some(VerticalLayout(2.0))
    size.height := contentHeight
  }

  layout @= Some(VerticalLayout(0.0))
  size.width @= columns.map(_.width).sum
  size.height := contentHeight
  children ++= List(header, body)
  rows.attach { all =>
    body.children.clear()
    body.children ++= all.map(row)
  }

  private def row(value: T): Container = new Container {
    size.height := math.max(40.0, contentHeight() + 10.0)
    radius @= 6.0
    focusable @= true
    backgroundColor := (if (focused() || event.pointer.over()) Theme.raised() else Color.Clear)
    children ++= place(columns.map(_.cell(value)))
    event.click.attach(_ => onRow(value))
  }

  /** Each cell at its column's left, a little down from the row's top. */
  private def place(cells: List[Component]): List[Component] = {
    var left = 12.0
    columns.zip(cells).map { case (column, cell) =>
      cell.position.left @= left
      cell.position.top @= 10.0
      left = left + column.width
      cell
    }
  }
}
