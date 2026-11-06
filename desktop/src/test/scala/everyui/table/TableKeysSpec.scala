package everyui.table

import everyui.component.Component
import everyui.event.{Key, KeyEvent, Keys}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** A table's headings by the keys: reached, gone along, sorting with Space, into the rows with Down, a column widened
  * and narrowed with Alt and the arrows, and said as column headers with how the rows are sorted; and a data table's
  * column widened from the cell the keys are in. */
class TableKeysSpec extends FxSpec {
  case class Row(name: String, team: String)

  private def table(): VirtualTable[Row] = new VirtualTable[Row](Var(Vector(Row("b", "x"), Row("a", "y"))), _.name,
    _.name, Vector(
      GridColumn.text[Row]("name", "Name", 200.0, sorts = true)(_.name),
      GridColumn.text[Row]("team", "Team", 120.0)(_.team)))

  private def press(c: Component, name: String, alt: Boolean = false): Unit =
    fx(c.event.key.down @= KeyEvent(Keys.of(name), name, alt = alt))

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A table's headings" should {
    "be reached by the keys before its rows, gone along, and into the rows with Down" in {
      val t = fx(table())
      onScreen(t) {
        val (name, team) = fx((t.header.cells(0), t.header.cells(1)))
        fx(Focus.candidates(t).toList.take(3)) shouldBe List(name, team, t.list)
        fx(name.focus())
        press(name, "ArrowRight")
        fx(Focus.focused()) shouldBe Some(team)
        press(team, "ArrowLeft")
        fx(Focus.focused()) shouldBe Some(name)
        press(name, "ArrowDown")
        fx(Focus.focused()) shouldBe Some(t.list)
      }
    }
    "sort with Space, saying how, and widen and narrow a column with Alt and the arrows" in {
      val t = fx(table())
      onScreen(t) {
        val name = fx(t.header.cells(0))
        fx(name.semantics().role) shouldBe "columnheader"
        fx(name.semantics().sort) shouldBe ""
        press(name, " ")
        fx(t.sort().map(s => (s.column, s.ascending))) shouldBe Some(("name", true))
        fx(name.semantics().sort) shouldBe "ascending"
        val was = fx(t.widthOf("name"))
        fx(name.keepsEvent(KeyEvent(Key.ArrowRight, "ArrowRight", alt = true))) shouldBe true
        press(name, "ArrowRight", alt = true)
        fx(t.widthOf("name")) shouldBe was + TableHeader.step
        press(name, "ArrowLeft", alt = true)
        fx(t.widthOf("name")) shouldBe was
      }
    }
  }

  "A data table" should {
    "widen and narrow the column the keys are in with Alt and the arrows" in {
      val t = fx(DataTable(Vector(DataColumn("Persona"), DataColumn("Spend")), Vector(Vector("Grace", "$1"))))
      onScreen(t) {
        fx(t.size.width @= 600.0)
        val was = fx(t.columnWidth(0))
        val grid = fx(t.children().collectFirst { case s: everyui.component.ScrollContainer => s }.get.content
          .children().head)
        fx(grid.event.key.down @= KeyEvent(Key.ArrowRight, "ArrowRight", alt = true))
        eventually(t.columnWidth(0) == was + TableHeader.step) shouldBe true
        fx(grid.event.key.down @= KeyEvent(Key.ArrowLeft, "ArrowLeft", alt = true))
        eventually(t.columnWidth(0) == was) shouldBe true
      }
    }
  }
}
