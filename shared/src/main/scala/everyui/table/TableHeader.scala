package everyui.table

import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.component.types.{FontWeight, Semantics, TextAlign}
import everyui.event.{Key, KeyEvent, PointerEvent}
import everyui.font.MaterialIcon
import everyui.i18n.{Direction, Words}
import everyui.list.{ListRow, Pointers}
import everyui.render.Platform
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
      // at the end, the arrow before the title (at the start where the column is too narrow for both)
      private def atEnd: Boolean =
        column.align == TextAlign.End && size.width() >= title.size.width() + ListRow.inset * 2.0 + 18.0
      title.position.left := ListRow.along(if (atEnd) size.width() - ListRow.inset - title.size.width() else ListRow.inset,
        title.size.width(), size.width())
      title.position.top := (size.height() - title.size.height()) / 2.0
      arrow.position.left := ListRow.along(if (atEnd) size.width() - ListRow.inset - title.size.width() - 18.0
        else ListRow.inset + title.size.width() + 4.0, 14.0, size.width())
      arrow.position.top := (size.height() - 14.0) / 2.0
      handle.size.height := size.height()
      handle.position.left := ListRow.along(size.width() - TableHeader.grip, TableHeader.grip, size.width())
      size.width.attach(w => clipTo(w, size.height()))
      size.height.attach(h => clipTo(size.width(), h))
      children ++= List(title, arrow, handle)
      if (column.sorts) {
        cursor @= "pointer"
        event.click.attach(_ => if (!resized) table.sortBy(key))
      }
      // a column header to a screen reader, saying how the rows are sorted by it
      semantics := Semantics("columnheader", sort = table.sort().filter(_.column == key)
        .map(s => if (s.ascending) "ascending" else "descending").getOrElse(""))
      private val sortIt = () => if (column.sorts) table.sortBy(key)
      keyed(this, Some(key), sortIt)
      override def keepsEvent(e: KeyEvent): Boolean = keyFor(this, Some(key), sortIt, e).nonEmpty
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

  /** A heading the keys reach (not where a remote moves the focus): Left and Right to the heading beside it, Down into
    * the rows, Space doing what a click does (sorting, or choosing every row), and, on a column's heading, Alt with
    * Left or Right making the column narrower or wider by `TableHeader.step`. */
  private def keyed(cell: Container, column: Option[String], act: () => Unit): Unit = {
    cell.focusable @= !Platform.remoteNavigation()
    // a click on it (to sort) leaves the focus where it was, in the rows
    cell.clickLeavesFocus = true
    // lit while the keys have it
    cell.backgroundColor := (if (cell.focusShown()) Theme.controlLit() else everyui.Color.Clear)
    cell.event.key.down.attach(e => keyFor(cell, column, act, e).foreach(_()))
  }

  private def keyFor(cell: Container, column: Option[String], act: () => Unit, e: KeyEvent): Option[() => Unit] = {
    val on = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    val shown = cells.filter(c => c.visible() && c.focusable())
    val at = shown.indexWhere(_ eq cell)
    if (e.ctrl || e.meta || e.shift) None
    else if (e.alt) column.filter(_ => e.key == on || e.key == back).map(key => () =>
      table.resize(key, table.widths(), if (e.key == on) TableHeader.step else -TableHeader.step))
    else if (e.key == Key.Space) Some(act)
    else if (e.key == Key.ArrowDown) Some(() => table.list.focus())
    else if (e.key == on || e.key == back) Some(() => {
      val to = at + (if (e.key == on) 1 else -1)
      if (to >= 0 && to < shown.size) shown(to).focus()
    })
    else None
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
      semantics := Semantics.checkbox(Words.selectAll, table.list.count > 0 && table.chosenCount >= table.list.count)
      keyed(this, None, () => table.toggleAll())
      override def keepsEvent(e: KeyEvent): Boolean = keyFor(this, None, () => table.toggleAll(), e).nonEmpty
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
  size.width.attach(w => clipTo(w, size.height()))

  override def kind: String = "TableHeader"
}

object TableHeader {
  val height: Double = 30.0
  /** How wide a column's resize handle is. */
  val grip: Double = 6.0
  /** How far Alt with Left or Right widens or narrows a column, in pixels. */
  val step: Double = 16.0
}
