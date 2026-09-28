package everyui.table

import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.FontWeight
import everyui.event.PointerEvent
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.{ListRow, Pointers}
import everyui.theme.Theme
import reactify.*

/** The headings of a `VirtualTable`: each column's title, an arrow on the one it is sorted by, a click to sort by one
  * that sorts (up, down, then not at all), and a handle at each column's end that a pointer drags to widen it. */
class TableHeader[T](table: VirtualTable[T]) extends Container {
  val cells: Vector[Container] = table.columns.map { column =>
    val key = column.key
    val title = new TextView {
      content @= column.title
      font.size @= 12.0
      font.weight @= FontWeight.SemiBold
      color := Theme.muted()
    }
    val arrow = new MaterialIconView(MaterialIcon.ArrowUpward, 14.0) {
      icon := (if (table.sort().exists(s => s.column == key && !s.ascending)) MaterialIcon.ArrowDownward
        else MaterialIcon.ArrowUpward)
      color := Theme.accent()
      visible := table.sort().exists(_.column == key)
    }
    val handle = new Container {
      size.width @= TableHeader.grip
      cursor @= "col-resize"
    }
    new Container {
      size.width := table.widthOf(key)
      size.height := TableHeader.this.size.height()
      visible := table.widthOf(key) > 0.0
      position.left := ListRow.along(table.startOf(key) - table.scrollX(), size.width(), TableHeader.this.size.width())
      title.position.left := ListRow.along(ListRow.inset, title.size.width(), size.width())
      title.position.top := (size.height() - title.size.height()) / 2.0
      arrow.position.left := ListRow.along(ListRow.inset + title.size.width() + 4.0, 14.0, size.width())
      arrow.position.top := (size.height() - 14.0) / 2.0
      handle.size.height := size.height()
      handle.position.left := ListRow.along(size.width() - TableHeader.grip, TableHeader.grip, size.width())
      size.width.attach(w => node.setClip(w, size.height()))
      size.height.attach(h => node.setClip(size.width(), h))
      children ++= List(title, arrow, handle)
      if (column.sort.nonEmpty) event.click.attach(_ => if (!resized) table.sortBy(key))
      handle.event.click.attach(_.consume())
      handle.event.pointer.down.attach { e =>
        if (e.button == PointerEvent.PrimaryButton) {
          e.consume()
          val from = e.x
          val was = table.widthOf(key)
          resized = false
          Pointers.track(m => {
            resized = true
            val along = if (Direction.rtl()) from - m.x else m.x - from
            table.widths @= table.widths() + (key -> math.max(column.minWidth, was + along))
          }, _ => everyui.render.Platform.later(() => resized = false))
        }
      }
    }
  }
  // a drag on a handle that ends over its heading is not a click on it
  private var resized = false

  size.height @= TableHeader.height
  backgroundColor := Theme.surface()
  children ++= cells
  private val rule = new Container {
    size.height @= 1.0
    size.width := TableHeader.this.size.width()
    position.top := TableHeader.this.size.height() - 1.0
    backgroundColor := Theme.border()
  }
  children += rule
  size.width.attach(w => node.setClip(w, size.height()))

  override def kind: String = "TableHeader"
}

object TableHeader {
  val height: Double = 30.0
  /** How wide a column's resize handle is. */
  val grip: Double = 6.0
}
