package everyui.table

import everyui.async.Task
import everyui.component.Component
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.paging.{Page, PagedSource}
import everyui.{FxSpec, ui}

/** A `DataTable`'s columns widened and narrowed by the handle at each heading's end, for a table of its own rows and of
  * a server's alike: the width kept by the column's key (`widths`, the app's to keep and give back), never narrower
  * than `narrowest`, the drag never a sort; a double-click on the handle fitting the column to its widest cell. */
class ColumnWidthsSpec extends FxSpec {
  private def onScreen[T](view: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      view.size.width @= 800.0
      ui.children += view
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
      view.dispose()
    }
  }

  // the handle of column `c` dragged `by` along
  private def drag(t: DataTable, c: Int, by: Double): Unit = {
    val h = t.handles(c)
    val x = h.absoluteLeft + 2.0
    val y = h.absoluteTop + 2.0
    h.event.pointer.down @= PointerEvent(PointerKind.Down, x, y)
    ui.event.pointer.move @= PointerEvent(PointerKind.Move, x + by / 2.0, y)
    ui.event.pointer.move @= PointerEvent(PointerKind.Move, x + by, y)
    ui.event.pointer.up @= PointerEvent(PointerKind.Up, x + by, y)
  }

  private def tables(): List[(String, DataTable)] = fx {
    val own = DataTable(Vector(DataColumn("Name"), DataColumn("Role")),
      Vector(Vector("Ada", "Researcher"), Vector("Grace", "Reviewer of very long things indeed")))
    val source = new PagedSource[Vector[DataCell]](_.head.text, pageSize = 10)(r => Task.pure(Right(Page(
      Vector(Vector(DataCell("a"), DataCell("one")), Vector(DataCell("b"), DataCell("a rather longer cell"))), Some(2)))))
    val served = DataTable.paged(Vector(DataColumn("Id", key = "id"), DataColumn("Text", key = "text")), source)
    served.maxHeight @= 300.0
    List("its own rows" -> own, "a server's rows" -> served)
  }

  "A data table's columns" should {
    for ((name, t) <- tables()) {
      s"be dragged wider and narrower at their headings' ends, and fitted on a double-click ($name)" in {
        onScreen(t) {
          val was = fx(t.columnWidth(0))
          val key = fx(t.keyOf(0))
          fx(t.handles.size) shouldBe 2
          fx(drag(t, 0, 80.0))
          fx(t.widths().get(key)) shouldBe Some(was + 80.0)
          fx(t.columnWidth(0)) shouldBe was + 80.0
          // the drag sorted nothing
          fx(t.sort()) shouldBe None
          // never narrower than the narrowest
          fx(drag(t, 0, -1000.0))
          fx(t.columnWidth(0)) shouldBe DataTable.narrowest
          // a double-click on the handle: as wide as its widest cell on one line
          fx {
            t.handles(0).event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0)
            t.handles(0).event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0)
          }
          val fitted = fx(t.columnWidth(0))
          fitted should be > DataTable.narrowest
          fitted should be < was + 80.0
          fx(t.sort()) shouldBe None
          // given back by the app, as it kept them
          fx(t.widths @= Map(key -> 222.0))
          fx(t.columnWidth(0)) shouldBe 222.0
          fx(t.resetWidths())
          fx(t.columnWidth(0)) shouldBe was
        }
      }
    }
  }
}
