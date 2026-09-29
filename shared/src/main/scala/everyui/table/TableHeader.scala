package everyui.table

import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.component.types.FontWeight
import everyui.event.PointerEvent
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.{ListRow, Pointers}
import everyui.theme.Theme
import reactify.*

/** The headings of a `VirtualTable`: each column's title, an arrow on the one it is sorted by, a click to sort by one
  * that sorts (up, down, then not at all), and a handle at each column's end that a pointer drags to widen it. Over a
  * column of tick boxes, one that chooses every row or none, and shows which (a dash where only some are). The
  * headings follow the table's columns as they change. */
class TableHeader[T](table: VirtualTable[T]) extends Container {
  private var made: Vector[(GridColumn[T], Container)] = Vector.empty
  // a drag on a handle that ends over its heading is not a click on it
  private var resized = false

  /** The headings, in the order the columns show. */
  def cells: Vector[Container] = made.map(_._2)

  private def heading(column: GridColumn[T]): Container = {
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
      if (column.sorts) {
        cursor @= "pointer"
        event.click.attach(_ => if (!resized) table.sortBy(key))
      }
      handle.event.click.attach(_.consume())
      handle.event.pointer.down.attach { e =>
        if (e.button == PointerEvent.PrimaryButton) {
          e.consume()
          val from = e.x
          val was = table.widths()
          resized = false
          Pointers.track(m => {
            resized = true
            table.resize(key, was, if (Direction.rtl()) from - m.x else m.x - from)
          }, _ => everyui.render.Platform.later(() => resized = false))
        }
      }
    }
  }

  // over the tick boxes: every row, none, or some
  private def checkAll(): Container = {
    val key = VirtualTable.checkKey
    val box = new MaterialIconView(MaterialIcon.CheckBoxOutlineBlank, 18.0) {
      icon := {
        val chosen = table.chosenCount
        if (chosen == 0) MaterialIcon.CheckBoxOutlineBlank
        else if (chosen >= table.list.count && table.list.count > 0) MaterialIcon.CheckBox
        else MaterialIcon.IndeterminateCheckBox
      }
      color := (if (table.chosenCount > 0) Theme.accent() else Theme.muted())
    }
    new Container {
      size.width := table.widthOf(key)
      size.height := TableHeader.this.size.height()
      visible := table.widthOf(key) > 0.0
      position.left := ListRow.along(table.startOf(key) - table.scrollX(), size.width(), TableHeader.this.size.width())
      box.position.left := (size.width() - 18.0) / 2.0
      box.position.top := (size.height() - 18.0) / 2.0
      cursor @= "pointer"
      children += box
      event.click.attach(_ => table.toggleAll())
    }
  }

  private def build(columns: Vector[GridColumn[T]]): Unit = {
    val before = made
    made = columns.map { column =>
      before.find(_._1 == column).getOrElse((column, if (column.key == VirtualTable.checkKey) checkAll() else heading(column)))
    }
    val gone = before.filterNot(m => made.exists(_._1 == m._1))
    if (gone.nonEmpty || made != before) {
      children @= made.map(_._2) :+ rule
      gone.foreach(_._2.dispose())
    }
  }

  size.height @= TableHeader.height
  backgroundColor := Theme.surface()
  private val rule: Component = new Container {
    size.height @= 1.0
    size.width := TableHeader.this.size.width()
    position.top := TableHeader.this.size.height() - 1.0
    backgroundColor := Theme.border()
  }
  build(table.shown())
  follow(table.shown, (c: Vector[GridColumn[T]]) => build(c))
  size.width.attach(w => node.setClip(w, size.height()))

  override def kind: String = "TableHeader"
}

object TableHeader {
  val height: Double = 30.0
  /** How wide a column's resize handle is. */
  val grip: Double = 6.0
}
