package everyui.markdown

import everyui.component.{Component, Container, RichText, ScrollContainer}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.ScrollEvent

/** A Markdown table in something that scrolls (a chat's messages, a page): as tall as all its rows, wrapped or not,
  * plain or sorting, so it never scrolls up and down on its own; the wheel turned down over it scrolls what it is in, and
  * only a sideways turn, or Shift with the wheel, scrolls the table where it is wider than the room. */
class TableScrollSpec extends FxSpec {
  // seven columns of phrases that wrap, and of words too long for a narrow room
  private val wide = "| Module | Owner | Status | Coverage | Last change | Risk | Notes |\n|---|---|---|---|---|---|---|\n" +
    (1 to 6).map(i => s"| network$i | Sam | one failing test in the socket reconnect path | 81% | moved the retry timer " +
      "into the shared scheduler | medium | the drop test fails only when the server closes first |\n").mkString

  // the view in a page 300 high that scrolls down
  private def onScreen[T](view: MarkdownView)(test: ScrollContainer => T): T = {
    val page = fx {
      val page = new ScrollContainer
      page.size.width @= 800.0
      page.size.height @= 300.0
      page.content.children += view
      ui.children.clear()
      ui.children += page
      page
    }
    try {
      eventually(view.children().nonEmpty && view.size.height() > 0.0) shouldBe true
      test(page)
    } finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def all(c: Component): Vector[Component] = c match {
    case k: Container => k +: k.children().flatMap(all)
    case _ => Vector(c)
  }
  private def table(view: MarkdownView): MarkdownTable = view.children().head.asInstanceOf[MarkdownTable]
  // the table's own scrolls: the one along it, and any down it
  private def rail(c: Component): ScrollContainer = all(c).collectFirst { case s: ScrollContainer if s.horizontal => s }.get
  private def downs(c: Component): Vector[ScrollContainer] = all(c).collect { case s: ScrollContainer if !s.horizontal => s }
  private def cells(c: Component): Vector[RichText] = all(c).collect { case t: RichText if t.semantics().role == "cell" => t }

  // the table as tall as its rows, and nothing in it with more down it than it shows
  private def whole(view: MarkdownView): Boolean = {
    val t = table(view)
    val rows = all(t).collect { case r: Container if r.semantics().role == "row" && r.visible() => r }
    val reach = rows.map(r => r.absoluteTop + r.size.height()).max - rows.map(_.absoluteTop).min
    rows.nonEmpty && cells(t).forall(_.size.height() > 0.0) && math.abs(t.size.height() - reach) <= 2.5 &&
      downs(t).forall(s => s.size.height() >= s.content.size.height() - 0.5) &&
      rows.forall(r => r.size.height() >= r.children().collect { case c: RichText => c.size.height() }.maxOption.getOrElse(0.0))
  }

  // the wheel over `on`, as JavaFX sends it (down is a negative delta), heard by it and everything it is in
  private def wheel(on: Component, dx: Double, dy: Double, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(on.node.fx, new ScrollEvent(ScrollEvent.SCROLL, 0, 0, 0, 0, shift, false, false, false,
      false, false, dx, dy, dx, dy, ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0,
      0, null))
  }

  "A Markdown table in a scroll" should {
    "be as tall as its wrapped rows, plain and sorting, and again once its width changes" in {
      val view = fx(MarkdownView(wide, 700.0))
      onScreen(view) { _ =>
        eventually(whole(view)) shouldBe true
        val plain = fx(table(view).size.height())
        fx(table(view).engage())
        eventually(table(view).rich.nonEmpty && whole(view)) shouldBe true
        // taller than a data table's own most, which a Markdown table has none of
        fx(table(view).size.height()) should be > 420.0
        fx(math.abs(table(view).size.height() - plain)) should be <= 2.5
        // narrower, the words wrap onto more lines, and it grows with them
        fx(view.size.width @= 420.0)
        eventually(table(view).size.height() > plain + 20.0 && whole(view)) shouldBe true
        // and wider again, it shrinks back
        fx(view.size.width @= 700.0)
        eventually(math.abs(table(view).size.height() - plain) <= 2.5 && whole(view)) shouldBe true
      }
    }
    "pass the wheel turned down to what it is in, and scroll sideways only by a sideways turn or Shift" in {
      val view = fx(MarkdownView(wide, 300.0))
      onScreen(view) { page =>
        fx(table(view).engage())
        eventually(table(view).rich.nonEmpty && whole(view)) shouldBe true
        val along = fx(rail(table(view)))
        fx(along.content.size.width()) should be > 300.0
        val cell = fx(cells(table(view))(3))
        wheel(cell, 0.0, -40.0)
        eventually(page.offset() == 40.0) shouldBe true
        fx(along.offset()) shouldBe 0.0
        fx(downs(table(view)).forall(_.offset() == 0.0)) shouldBe true
        // a trackpad's sideways turn moves the table, and not the page
        wheel(cell, -30.0, 0.0)
        eventually(along.offset() == 30.0) shouldBe true
        fx(page.offset()) shouldBe 40.0
        // and Shift with the wheel, as a browser has it
        wheel(cell, 0.0, -30.0, shift = true)
        eventually(along.offset() == 60.0) shouldBe true
      }
    }
    "scroll its plain table sideways the same way" in {
      val view = fx(MarkdownView(wide, 300.0))
      fx(view.richTables @= false)
      onScreen(view) { page =>
        eventually(view.children().head.isInstanceOf[TableView]) shouldBe true
        val t = fx(view.children().head)
        val along = fx(rail(t))
        eventually(along.content.size.width() > 300.0) shouldBe true
        val cell = fx(all(t).collect { case c: RichText if c.semantics().role == "cell" => c }(3))
        wheel(cell, 0.0, -40.0)
        eventually(page.offset() == 40.0) shouldBe true
        fx(along.offset()) shouldBe 0.0
        wheel(cell, -30.0, 0.0)
        eventually(along.offset() == 30.0) shouldBe true
        fx(page.offset()) shouldBe 40.0
      }
    }
  }
}
