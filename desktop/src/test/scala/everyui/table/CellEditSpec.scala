package everyui.table

import everyui.async.Task
import everyui.component.{Component, Container, ScrollContainer}
import everyui.event.{Key, KeyEvent, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.list.RowEditor
import everyui.paging.{Page, PagedSource}
import everyui.{FxSpec, ui}

/** A `DataTable`'s cells typed over in place where it is `editable`: a double-click on a cell (or F2 on the one the keys
  * are on) opens a field with its words, Enter keeps what was typed in the row (`edited` hears it), Escape keeps what
  * it was, and the app's `commit` can refuse it, saying why; a column that is not editable, a table that is not, and a
  * single click open nothing. A server's row takes the words in its source. */
class CellEditSpec extends FxSpec {
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

  private def scroll(t: DataTable): ScrollContainer = t.children().collectFirst { case s: ScrollContainer => s }.get
  private def grid(t: DataTable): Container = scroll(t).content.children().head.asInstanceOf[Container]
  private def rowsScroll(t: DataTable): ScrollContainer = grid(t).children().collectFirst { case s: ScrollContainer => s }.get
  private def body(t: DataTable): Container = rowsScroll(t).content.children().head.asInstanceOf[Container]
  private def row(t: DataTable, name: String): DataRow =
    body(t).children().collectFirst { case r: DataRow if r.content.head.text == name => r }.get
  private def editor(t: DataTable): Option[RowEditor] = grid(t).children().collectFirst { case e: RowEditor => e }
  private def click(t: DataTable, cell: Component): Unit =
    grid(t).event.click @= PointerEvent(PointerKind.Click, cell.absoluteLeft + 2.0, cell.absoluteTop + 2.0)
  private def twice(t: DataTable, cell: Component): Unit = {
    click(t, cell)
    click(t, cell)
  }

  private def made(): DataTable = fx {
    DataTable(Vector(DataColumn("Name"), DataColumn("Role"), DataColumn("Id", editable = false)),
      Vector(Vector("Ada", "Researcher", "1"), Vector("Grace", "Reviewer", "2"), Vector("Alan", "Planner", "3")))
  }

  "A data table's cells" should {
    "open nothing where the table is not editable, nor on a single click" in {
      val t = made()
      onScreen(t) {
        fx(twice(t, row(t, "Grace").cells(1)))
        fx(editor(t)) shouldBe None
        fx(t.editable @= true)
        fx(click(t, row(t, "Grace").cells(1)))
        fx(editor(t)) shouldBe None
        // nor on a column that is not editable
        fx(twice(t, row(t, "Grace").cells(2)))
        fx(editor(t)) shouldBe None
      }
    }
    "be typed over on a double-click, Enter keeping it in the row and Escape what it was" in {
      val t = made()
      var heard: List[CellEdit] = Nil
      fx {
        t.editable @= true
        t.edited.attach(e => heard = heard :+ e)
      }
      onScreen(t) {
        fx(twice(t, row(t, "Grace").cells(1)))
        val e = fx(editor(t)).get
        fx(e.input.text()) shouldBe "Reviewer"
        fx {
          e.input.text @= "Admiral"
          e.event.key.down @= KeyEvent(Key.Enter, "Enter")
        }
        eventually(editor(t).isEmpty) shouldBe true
        fx(t.cells()(1).map(_.text)) shouldBe Vector("Grace", "Admiral", "2")
        heard shouldBe List(CellEdit(1, 1, "Reviewer", "Admiral"))
        // Escape: as it was
        fx(twice(t, row(t, "Alan").cells(0)))
        fx {
          editor(t).get.input.text @= "Turing"
          editor(t).get.event.key.down @= KeyEvent(Key.Escape, "Escape")
        }
        fx(editor(t)) shouldBe None
        fx(t.cells()(2).head.text) shouldBe "Alan"
        heard.size shouldBe 1
      }
    }
    "be refused by the app's commit, saying why, and open on F2 where the keys are" in {
      val t = made()
      fx {
        t.editable @= true
        t.commit @= (e => Task.pure(if (e.text.isEmpty) Some("A name, please") else None))
      }
      onScreen(t) {
        fx {
          t.focus()
          grid(t).event.key.down @= KeyEvent(Key.ArrowDown, "ArrowDown")
          grid(t).event.key.down @= KeyEvent(Key.Other, "F2")
        }
        val e = fx(editor(t)).get
        fx(e.input.text()) shouldBe "Ada"
        fx {
          e.input.text @= ""
          e.event.key.down @= KeyEvent(Key.Enter, "Enter")
        }
        fx(editor(t).nonEmpty) shouldBe true
        fx(e.error()) shouldBe "A name, please"
        fx(t.cells()(0).head.text) shouldBe "Ada"
        fx {
          e.input.text @= "Augusta"
          e.event.key.down @= KeyEvent(Key.Enter, "Enter")
        }
        eventually(editor(t).isEmpty) shouldBe true
        fx(t.cells()(0).head.text) shouldBe "Augusta"
      }
    }
    "take what is typed into a server's row in its source" in {
      val source = fx(new PagedSource[Vector[DataCell]](_.head.text, pageSize = 10)(request => Task.pure(Right(Page(
        (request.offset until math.min(5, request.offset + request.limit)).toVector
          .map(i => Vector(DataCell("id" + i.toString), DataCell("item " + i.toString))), Some(5))))))
      val t = fx(DataTable.paged(Vector(DataColumn("Id", editable = false), DataColumn("Item")), source))
      fx {
        t.maxHeight @= 300.0
        t.editable @= true
      }
      onScreen(t) {
        eventually(body(t).children().exists(_.isInstanceOf[DataRow])) shouldBe true
        fx(twice(t, row(t, "id3").cells(1)))
        fx {
          editor(t).get.input.text @= "renamed"
          editor(t).get.event.key.down @= KeyEvent(Key.Enter, "Enter")
        }
        eventually(editor(t).isEmpty) shouldBe true
        fx(source.at(3).map(_(1).text)) shouldBe Some("renamed")
        eventually(row(t, "id3").content(1).text == "renamed") shouldBe true
      }
    }
  }
}
