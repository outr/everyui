package everyui.list

import everyui.component.{Component, Container, ScrollContainer}
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.layout.LayoutPass
import everyui.perf.Quality
import everyui.{FxSpec, ui}
import reactify.*

/** A grid of a thousand cells, made a row at a time near the view: as many across as fit, the focus moved down to the
  * same place in rows not made yet (the last, in a shorter row), and rows kept as cells are appended. */
class VirtualGridSpec extends FxSpec {
  override protected def keyed: Boolean = true

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Focus.focused @= None
    }
    super.afterAll()
  }

  private def cell(i: Int, width: Double): Component = {
    val c = new Container
    c.size.width @= width
    c.size.height @= width * 1.5
    c.focusable @= true
    c.semantics @= everyui.component.types.Semantics(label = "cell " + i.toString)
    c
  }

  /** A page 600 tall holding a grid 520 wide of `n` cells at least 100 wide, 16 apart. */
  private def grid(n: Int): (ScrollContainer, VirtualGrid[Int]) = fx {
    ui.children.clear()
    Quality.forget()
    val s = new ScrollContainer
    s.size.width @= 520.0
    s.size.height @= 600.0
    val g = new VirtualGrid(Var(Vector.tabulate(n)(i => i)), (i: Int) => i.toString, GridColumns.adaptive(100.0),
      16.0)(cell)
    g.size.width @= 520.0
    s.content.children += g
    ui.children += s
    LayoutPass.settle()
    (s, g)
  }

  private def press(key: String): Unit = fx {
    Pointer.usedAt = -1.0e9
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def settled(): Unit = {
    fx(LayoutPass.settle())
    fx(LayoutPass.settle())
  }

  private def focusedLabel: Option[String] = fx(Focus.focused().map(_.semantics().label))

  "A VirtualGrid" should {
    "make the rows near the view, as many cells across as fit" in {
      val (s, g) = grid(1000)
      settled()
      fx(g.perRow) shouldBe 4
      fx(g.count) shouldBe 250
      // cells 118 wide, rows 177 tall: the view and a view below it are seven rows, never the 250
      fx(g.madeCount) should be >= 4
      fx(g.madeCount) should be <= 9
      fx(s.scrollTo(193.0 * 200))
      settled()
      fx(g.cellAt(801).nonEmpty) shouldBe true
      fx(GridColumns.fixed(3).across(520.0, 16.0)) shouldBe 3
      fx(GridColumns.fixed(3).cellWidth(520.0, 16.0)) shouldBe ((520.0 - 32.0) / 3.0)
    }
    "move the focus down to the same place in rows not made yet, and to the last of a shorter row" in {
      val (_, g) = grid(42)
      fx(g.makeCell(2).get.focus())
      (1 to 9).foreach { r =>
        press("ArrowDown")
        settled()
        focusedLabel shouldBe Some("cell " + (r * 4 + 2).toString)
      }
      // the last row holds 40 and 41 alone: down from 38's place lands on 41, its last
      press("ArrowDown")
      settled()
      focusedLabel shouldBe Some("cell 41")
      press("ArrowUp")
      settled()
      focusedLabel shouldBe Some("cell 37")
      fx(g.makeCell(39).get.focus())
      press("ArrowDown")
      settled()
      focusedLabel shouldBe Some("cell 41")
      press("ArrowRight")
      settled()
      focusedLabel shouldBe Some("cell 41")
      press("ArrowLeft")
      settled()
      focusedLabel shouldBe Some("cell 40")
    }
    "keep the rows whose cells stay as more are appended" in {
      val (s, g) = grid(10)
      settled()
      val first = fx(g.rowAt(0).get)
      fx(g.cells @= g.cells() ++ Vector.tabulate(30)(i => 10 + i))
      settled()
      fx(g.rowAt(0).exists(_ eq first)) shouldBe true
      fx(g.count) shouldBe 10
      fx(s.scrollTo(193.0 * 8))
      settled()
      fx(g.cellAt(39).nonEmpty) shouldBe true
    }
  }
}
