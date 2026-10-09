package everyui.markdown

import everyui.component.{Component, Container, RichText, ScrollContainer}
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** A Markdown table on the desktop: on one line a column where it fits, as before; its cells' words wrapped to share a
  * narrow width, none cut; and scrolled only where a word cannot wrap, with a shade at the edge it goes on past. */
class TableViewSpec extends FxSpec {
  private val prose = "| Step | What it does | Where it fails |\n|---|---|---|\n" +
    "| Parse | Reads the request and checks its fields | A field missing or of the wrong type |\n" +
    "| Load | Fetches the settings | The store times out under load |"
  private val path = "| File | Notes |\n|---|---|\n" +
    "| `shared/src/main/scala/everyui/markdown/TableView.scala` | Where tables go |"

  private def onScreen[T](view: MarkdownView)(test: TableView => T): T = {
    fx {
      // the table as it is drawn where it neither sorts nor filters (a Roku, an Apple TV, a view that says so); the
      // table that does is DataTableSpec's
      view.richTables @= false
      ui.children.clear()
      ui.children += view
    }
    try {
      eventually(view.children().nonEmpty && view.size.height() > 0.0) shouldBe true
      test(fx(view.children().head.asInstanceOf[TableView]))
    } finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def scroll(table: TableView): ScrollContainer =
    table.children().collectFirst { case s: ScrollContainer => s }.get
  private def grid(table: TableView): Container = scroll(table).content.children().head.asInstanceOf[Container]
  // the cells drawn, row by row
  private def rows(table: TableView): Vector[Vector[RichText]] = grid(table).children().collect {
    case row: Container if row.children().exists(_.isInstanceOf[RichText]) =>
      row.children().collect { case t: RichText => t }
  }
  private def shades(table: TableView): (Component, Component) = (table.children()(2), table.children()(3))

  "A Markdown table" should {
    "lay its columns out on one line where they fit, no wider than its words" in {
      onScreen(MarkdownView(prose, 1000.0)) { table =>
        eventually(rows(table).flatten.forall(_.size.height() > 0.0)) shouldBe true
        val cells = fx(rows(table))
        val line = fx(cells.head.head.size.height())
        fx(cells.flatten.forall(c => math.abs(c.size.height() - line) < 1.0)) shouldBe true
        fx(grid(table).size.width()) should be < 1000.0
        fx(scroll(table).focusable()) shouldBe false
        val (left, right) = fx(shades(table))
        fx(left.visible()) shouldBe false
        fx(right.visible()) shouldBe false
      }
    }
    "wrap its cells' words to share a narrow width, a column of phrases giving up more than one of short words" in {
      val view = fx(MarkdownView(prose, 300.0))
      onScreen(view) { table =>
        eventually(rows(table).flatten.forall(_.size.height() > 0.0) && grid(table).size.width() <= 300.5) shouldBe true
        val cells = fx(rows(table))
        val line = fx(cells.head.head.size.height())
        // the phrases take more than one line, the step's single words one
        fx(cells(1)(1).size.height()) should be > line * 1.5
        fx(cells(1)(2).size.height()) should be > line * 1.5
        fx(cells.map(_.head.size.height()).forall(h => math.abs(h - line) < 1.0)) shouldBe true
        // every cell within its row and the table, its row as tall as its tallest cell
        val across = fx(grid(table).size.width())
        fx(cells.flatten.forall(c => c.position.left() + c.size.width() <= across + 0.5)) shouldBe true
        fx(cells.forall(r => r.head.parent().get.size.height() >= r.map(_.size.height()).max)) shouldBe true
        fx(scroll(table).focusable()) shouldBe false
        // narrower again, the words wrap again
        val tall = fx(table.size.height())
        fx(view.size.width @= 240.0)
        eventually(table.size.height() > tall) shouldBe true
      }
    }
    "scroll only where a word cannot wrap, a shade at the edge it goes on past" in {
      onScreen(MarkdownView(path, 330.0)) { table =>
        eventually(grid(table).size.width() > 330.0) shouldBe true
        val s = fx(scroll(table))
        fx(s.focusable()) shouldBe true
        fx(s.size.width()) shouldBe 330.0
        val (left, right) = fx(shades(table))
        fx(left.visible()) shouldBe false
        fx(right.visible()) shouldBe true
        fx(right.position.left() + right.size.width()) shouldBe 330.0
        // the notes keep their words on one line where the path is wider than the room anyway
        val cells = fx(rows(table))
        fx(math.abs(cells(1)(1).size.height() - cells(1)(0).size.height()) < 4.0) shouldBe true
        fx(s.scrollTo(10000.0))
        eventually(left.visible() && !right.visible()) shouldBe true
        fx(s.scrollTo(0.0))
        eventually(!left.visible() && right.visible()) shouldBe true
      }
    }
  }
}
