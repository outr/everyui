package everyui.component

import everyui.Color
import everyui.kit.Mirror
import everyui.layout.VerticalLayout
import everyui.theme.Theme
import reactify.*

/** Rows of `T` under a header, each column at its width. A row brightens under the pointer or the focus and
  * reports a click. */
class Table[T](columns: List[TableColumn[T]], onRow: T => Unit = (_: T) => ()) extends Container {
  val rows: Var[List[T]] = own(Var(Nil))

  private val header = new Container {
    size.width := Table.this.size.width()
    size.height @= 36.0
    children ++= place(columns.map(c => TextView(c.title, 12.0, Theme.muted())))
  }
  private val body = new Container {
    size.width := Table.this.size.width()
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
    size.width := Table.this.size.width()
    size.height := math.max(40.0, contentHeight() + 10.0)
    radius @= 6.0
    focusable @= true
    backgroundColor := (if (focusShown() || event.pointer.over()) Theme.raised() else Color.Clear)
    children ++= place(columns.map(_.cell(value)))
    event.click.attach(_ => onRow(value))
  }

  /** Each cell at its column's start, a little down from the row's top; mirrored in a right-to-left language. */
  private def place(cells: List[Component]): List[Component] = {
    var left = 12.0
    cells.zipWithIndex.map { case (cell, i) =>
      val at = left
      Mirror.place(cell, () => size.width(), () => at)
      cell.position.top @= 10.0
      left = left + columns(i).width
      cell
    }
  }
}
