package everyui.table

import everyui.component.{Component, Container, ScrollContainer}
import everyui.event.{Key, KeyEvent, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.theme.Theme
import everyui.{FxSpec, ui}

/** A `DataTable` whose rows open something (`onOpen`), on the desktop's nodes: a click and Enter open a row, by its
  * index in the table's cells however it is sorted, and mark it open until another opens or the app clears it; the
  * keys, Shift and Ctrl with a click still choose; its rows show the pointer's hand and a wash under it, and are
  * buttons named by their words, the open one current; and with no `onOpen` a click chooses as it always has. */
class DataTableOpenSpec extends FxSpec {
  private val columns = Vector(DataColumn("Persona"), DataColumn("Conversations"), DataColumn("Last active"))
  private val rows = Vector(
    Vector("Grace", "1,204", "Mar 3, 2026"),
    Vector("ada", "87", "2025-12-28"),
    Vector("Alan", "12,000", "9 October 2026"),
    Vector("Barbara", "", "2026-06-30"))

  private def onScreen[T](view: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      view.size.width @= 700.0
      ui.children += view
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def scroll(t: DataTable): ScrollContainer = t.children().collectFirst { case s: ScrollContainer => s }.get
  private def grid(t: DataTable): Container = scroll(t).content.children().head.asInstanceOf[Container]
  private def head(t: DataTable): DataRow = grid(t).children().collectFirst { case r: DataRow => r }.get
  private def body(t: DataTable): Container =
    grid(t).children().collectFirst { case s: ScrollContainer => s }.get.content.children().head.asInstanceOf[Container]
  private def row(t: DataTable, name: String): DataRow =
    body(t).children().collectFirst { case r: DataRow if r.content.head.text == name => r }.get
  private def names(t: DataTable): Vector[String] =
    body(t).children().collect { case r: DataRow if r.shown() => r.content.head.text }
  private def click(t: DataTable, cell: Component, shift: Boolean = false, ctrl: Boolean = false): Unit =
    grid(t).event.click @= PointerEvent(PointerKind.Click, cell.absoluteLeft + 2.0, cell.absoluteTop + 2.0, ctrl = ctrl,
      shift = shift)
  private def key(t: DataTable, k: Key, name: String, shift: Boolean = false): Unit =
    grid(t).event.key.down @= KeyEvent(k, name, shift = shift)

  private def enter(r: DataRow): Unit =
    r.event.pointer.enter @= PointerEvent(PointerKind.Enter, r.absoluteLeft + 2.0, r.absoluteTop + 2.0)

  // a table whose rows open, and what it opened
  private def opening(): (DataTable, () => Vector[Int]) = {
    var heard = Vector.empty[Int]
    val t = DataTable(columns, rows)
    t.onOpen @= Some((i: Int) => heard = heard :+ i)
    (t, () => heard)
  }

  "A data table whose rows open" should {
    "open a row on a click, by its index in the cells however it is sorted, and mark it open" in {
      val (t, heard) = fx(opening())
      onScreen(t) {
        eventually(names(t).size == 4 && head(t).size.height() > 0.0) shouldBe true
        fx(click(t, head(t).cells(0)))
        fx(names(t)) shouldBe Vector("ada", "Alan", "Barbara", "Grace")
        fx(click(t, row(t, "Alan").cells(1)))
        fx(heard()) shouldBe Vector(2)
        fx(t.opened()) shouldBe Some(2)
        fx(t.selection()) shouldBe Set.empty
        fx(row(t, "Alan").semantics().current) shouldBe true
        fx(row(t, "Alan").backgroundColor()) shouldBe DataRow.openFill(false)
        // another opened: it is the one marked
        fx(click(t, row(t, "ada").cells(0)))
        fx(heard()) shouldBe Vector(2, 1)
        fx(row(t, "Alan").semantics().current) shouldBe false
        fx(row(t, "ada").semantics().current) shouldBe true
        // the app closes what it opened
        fx(t.opened @= None)
        fx(row(t, "ada").semantics().current) shouldBe false
        fx(row(t, "ada").backgroundColor()) should not be DataRow.openFill(false)
      }
    }
    "draw the open row's wash where it shows under a sort, and its stripe again once it is closed" in {
      val (t, heard) = fx(opening())
      onScreen(t) {
        eventually(names(t).size == 4 && head(t).size.height() > 0.0) shouldBe true
        fx(click(t, head(t).cells(0)))
        fx(names(t)) shouldBe Vector("ada", "Alan", "Barbara", "Grace")
        // Alan shows second (a striped place) and is the third of the cells; Barbara shows third, where Alan was
        val look = t.style.look
        val stripe = fx(look.rowFill(1))
        fx(stripe) should not be everyui.Color.Clear
        fx(row(t, "Alan").backgroundColor()) shouldBe stripe
        fx(click(t, row(t, "Alan").cells(0)))
        fx(heard()) shouldBe Vector(2)
        fx(row(t, "Alan").backgroundColor()) shouldBe DataRow.openFill(false)
        fx(row(t, "Alan").backgroundColor()) should not be stripe
        // the rows about it as they were, the one showing at the open row's index among them
        fx(row(t, "ada").backgroundColor()) shouldBe look.rowFill(0)
        fx(row(t, "Barbara").backgroundColor()) shouldBe look.rowFill(2)
        fx(row(t, "Grace").backgroundColor()) shouldBe look.rowFill(3)
        fx(t.opened @= None)
        fx(row(t, "Alan").backgroundColor()) shouldBe stripe
      }
    }
    "open the row the keys are on with Enter, and still choose with Space, Shift and the arrows" in {
      val (t, heard) = fx(opening())
      onScreen(t) {
        eventually(names(t).size == 4) shouldBe true
        fx(t.focus())
        fx(key(t, Key.ArrowDown, "ArrowDown"))
        fx(key(t, Key.Enter, "Enter"))
        fx(heard()) shouldBe Vector(0)
        fx(t.opened()) shouldBe Some(0)
        fx(t.selection()) shouldBe Set.empty
        fx(key(t, Key.Space, " "))
        fx(t.selection()) shouldBe Set(0)
        fx(key(t, Key.ArrowDown, "ArrowDown", shift = true))
        fx(t.selection()) shouldBe Set(0, 1)
        fx(heard()) shouldBe Vector(0)
        // Enter on the header still sorts
        fx(key(t, Key.ArrowUp, "ArrowUp"))
        fx(key(t, Key.ArrowUp, "ArrowUp"))
        fx(key(t, Key.Enter, "Enter"))
        fx(t.sort()) shouldBe Some(ColumnSort(0, ascending = true))
        fx(heard()) shouldBe Vector(0)
      }
    }
    "choose with Ctrl and Shift and a click, and open nothing for them" in {
      val (t, heard) = fx(opening())
      onScreen(t) {
        eventually(names(t).size == 4 && head(t).size.height() > 0.0) shouldBe true
        fx(click(t, row(t, "ada").cells(0), ctrl = true))
        fx(t.selection()) shouldBe Set(1)
        fx(click(t, row(t, "Barbara").cells(0), shift = true))
        fx(t.selection()) shouldBe Set(1, 2, 3)
        fx(heard()) shouldBe Vector.empty
        fx(t.opened()) shouldBe None
        // copies are of the rows chosen, as before
        fx(t.tsv(t.chosen).linesIterator.toVector.size) shouldBe 4
      }
    }
    "show the pointer's hand and a wash under it, and be buttons named by their words" in {
      val (t, _) = fx(opening())
      onScreen(t) {
        eventually(names(t).size == 4) shouldBe true
        val grace = fx(row(t, "Grace"))
        fx(grace.cursor()) shouldBe "pointer"
        fx(grace.cells.map(_.cursor()).distinct) shouldBe Vector("pointer")
        fx(grace.semantics().role) shouldBe "row"
        fx(grace.semantics().description) shouldBe everyui.table.TableWords.opens
        fx(grace.semantics().label) shouldBe "Grace, 1,204, Mar 3, 2026"
        fx(grace.cells.map(_.semantics().role).distinct) shouldBe Vector("cell")
        fx(head(t).semantics().role) shouldBe "row"
        fx(head(t).cells.map(_.semantics().role).distinct) shouldBe Vector("columnheader")
        fx(enter(grace))
        fx(grace.backgroundColor()) shouldBe Theme.controlLit()
        fx(grace.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0))
        fx(grace.backgroundColor()) should not be Theme.controlLit()
      }
    }
  }

  "A data table whose rows open nothing" should {
    "choose on a click, show no hand or wash, and keep its rows as rows, as it always has" in {
      val t = fx(DataTable(columns, rows))
      onScreen(t) {
        eventually(names(t).size == 4 && head(t).size.height() > 0.0) shouldBe true
        fx(t.opening()) shouldBe false
        fx(click(t, row(t, "Alan").cells(0)))
        fx(t.selection()) shouldBe Set(2)
        fx(t.opened()) shouldBe None
        val alan = fx(row(t, "Alan"))
        fx(alan.cursor()) shouldBe ""
        fx(alan.cells.map(_.cursor()).distinct) shouldBe Vector("")
        fx(alan.semantics().role) shouldBe "row"
        fx(alan.semantics().current) shouldBe false
        fx(alan.cells.map(_.semantics().role).distinct) shouldBe Vector("cell")
        val grace = fx(row(t, "Grace"))
        val rest = fx(grace.backgroundColor())
        fx(enter(grace))
        fx(grace.backgroundColor()) shouldBe rest
        fx(key(t, Key.Enter, "Enter"))
        fx(t.opened()) shouldBe None
      }
    }
  }
}
