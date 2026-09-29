package everyui.markdown

import everyui.component.{Container, RichText, ScrollContainer}
import everyui.component.types.{Semantics, TextAlign, WhiteSpace}
import everyui.theme.Theme
import reactify.*

/** A table of a `MarkdownView`: its header on the raised colour, its rows under it, each column as wide as its widest
  * cell and its cells aligned as the table says, ruled in the border colour. Wider than `width`, it scrolls sideways
  * (the arrows scroll it once it has the focus). A cell's words stay on one line. */
class TableView(table: MdBlock.TableBlock, ctx: MarkdownContext, width: => Double) extends Container {
  private val pad = ctx.style.cellPadding
  private val points = ctx.style.points * 0.94
  private val columns = table.align.size
  private val cells: Vector[Vector[RichText]] = (table.header :: table.rows).toVector.zipWithIndex.map { r =>
    r._1.toVector.map(content => cell(content, r._2 == 0))
  }
  private val widths: Vector[Val[Double]] = (0 until columns).toVector.map { c =>
    own(Val(cells.foldLeft(0.0)((w, row) => math.max(w, row(c).size.width())) + pad * 2.0))
  }
  private val heights: Vector[Val[Double]] = cells.map { row =>
    own(Val(row.foldLeft(0.0)((h, cell) => math.max(h, cell.size.height())) + pad * 2.0))
  }
  private val across: Val[Double] = own(Val(widths.foldLeft(0.0)((w, c) => w + c())))
  private val down: Val[Double] = own(Val(heights.foldLeft(0.0)((h, r) => h + r())))
  private val grid = new Container {
    semantics @= Semantics("table")
    borderWidth @= 1.0
    borderColor := Theme.border()
  }
  // from its right only where its own words read right to left
  private val scroll = new ScrollContainer(horizontal = true, followsDirection = ctx.rightToLeft(List(table)))

  cells.indices.foreach { r =>
    val row = new Container {
      semantics @= Semantics("row")
      if (r == 0) backgroundColor := Theme.raised()
    }
    row.position.top := top(r)
    row.size.width := across()
    row.size.height := heights(r)()
    (0 until columns).foreach { c =>
      val cell = cells(r)(c)
      val align = table.align(c)
      cell.position.left := left(c) + pad + (if (align == TextAlign.Center) (widths(c)() - pad * 2.0 - cell.size.width()) / 2.0
        else if (align == TextAlign.End) widths(c)() - pad * 2.0 - cell.size.width() else 0.0)
      cell.position.top @= pad
      row.children += cell
    }
    // a rule under every row but the last
    if (r > 0) grid.children += rule(true, r)
    grid.children += row
  }
  (1 until columns).foreach(c => grid.children += rule(false, c))
  grid.size.width := across()
  grid.size.height := down()
  scroll.content.children += grid
  scroll.size.width := math.min(width, across())
  scroll.size.height := down()
  scroll.focusable := across() > width
  size.width := width
  size.height := down()
  children += scroll

  private def left(c: Int): Double = (0 until c).foldLeft(0.0)((x, i) => x + widths(i)())
  private def top(r: Int): Double = (0 until r).foldLeft(0.0)((y, i) => y + heights(i)())

  /** A rule across above row `at`, or down before column `at`. */
  private def rule(across: Boolean, at: Int): Container = new Container {
    backgroundColor := Theme.border()
    if (across) {
      position.top := top(at)
      size.width := TableView.this.across()
      size.height @= 1.0
    }
    else {
      position.left := left(at)
      size.width @= 1.0
      size.height := down()
    }
  }

  private def cell(content: List[MdInline], head: Boolean): RichText = new RichText {
    whiteSpace @= WhiteSpace.NoWrap
    font.size @= points
    color := (if (ctx.quiet) Theme.muted() else Theme.text())
    follow = ctx.onLink
    semantics @= Semantics(if (head) "columnheader" else "cell")
    direction @= ctx.direction
    runs := MarkdownRuns.parts(content, points, if (head) 700 else 400, ctx.breaks).flatMap(_.runs)
  }

  override def kind: String = "TableView"
}
