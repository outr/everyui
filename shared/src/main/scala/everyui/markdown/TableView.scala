package everyui.markdown

import everyui.{Gradient, GradientStop}
import everyui.component.{Container, RichText, ScrollContainer}
import everyui.component.types.{Semantics, TextAlign, WhiteSpace}
import everyui.i18n.Direction
import everyui.render.Gestures
import everyui.text.TextRun
import everyui.theme.Theme
import reactify.*

/** A table of a `MarkdownView`, drawn as its `tableLook` says (by default its header on a band of its own, every second
  * row striped, its corners rounded and no rules between its cells), its cells aligned as the table says: the start of
  * the cell at the right where its words read right to left, its first column at the right too. Its columns share
  * `width` as a browser's automatic table layout shares it (`TableWidths`): where every cell fits on one line each
  * column is as wide as its widest cell, as on a wide screen; where not, the cells' words wrap, a column of long phrases
  * giving up more than one of short words, and no word is cut. Only where its widest words alone are wider than `width`
  * (long code, a URL) does it scroll sideways (the arrows scroll it once it has the focus), a shade at each edge with
  * more beyond it. It is drawn exactly as the `DataTable` made in its place once it is wanted (`MarkdownTable`). */
class TableView(table: MdBlock.TableBlock, ctx: MarkdownContext, width: => Double) extends Container {
  private val look = ctx.tableLook()
  private val pad = look.across
  private val points = ctx.style.points * 0.94
  private val columns = table.align.size
  private val contents: Vector[Vector[List[TextRun]]] = (table.header :: table.rows).toVector.zipWithIndex.map { r =>
    r._1.toVector.map(content => MarkdownRuns.parts(content, points, if (r._2 == 0) look.headerWeight else 400,
      ctx.breaks, ctx.mentions(), ctx.pressesMentions()).flatMap(_.runs))
  }
  // its columns from the right where its words read right to left, as a DataTable finds it from its header
  private val mirrored = everyui.text.Bidi.rightToLeft(ctx.direction.value,
    table.header.map(MdInline.plain).mkString(" "), Direction.rtl())
  // each cell measured out of sight and taking no room: on one line, and a word to a line
  private val twins: Container = new Container {
    size.width @= 0.0
    size.height @= 0.0
    visible @= false
  }
  private val lines: Vector[Vector[RichText]] = contents.map(_.map(runs => twin(runs, words = false)))
  private val words: Vector[Vector[RichText]] = contents.map(_.map(runs => twin(runs, words = true)))
  private val least: Val[Vector[Double]] = own(Val(widest(words)))
  private val most: Val[Vector[Double]] = own(Val(widest(lines)))
  private val widths: Val[Vector[Double]] = own(Val(TableWidths(least(), most(), width)))
  private val cells: Vector[Vector[RichText]] = contents.indices.toVector.map { r =>
    contents(r).indices.toVector.map(c => cell(contents(r)(c), r, c))
  }
  private val heights: Vector[Val[Double]] = cells.indices.toVector.map { r =>
    own(Val(cells(r).foldLeft(0.0)((h, cell) => math.max(h, cell.size.height())) + look.vertical(r == 0) * 2.0))
  }
  private val across: Val[Double] = own(Val(widths().foldLeft(0.0)((w, c) => w + c)))
  private val down: Val[Double] = own(Val(heights.foldLeft(0.0)((h, r) => h + r())))
  private val grid = new Container {
    semantics @= Semantics("table")
    copyAs @= "table"
    radius @= look.radius
  }
  private val edge = TableLook.edge(look)
  private val scroll = new ScrollContainer(horizontal = true, followsDirection = mirrored)
  // where the table starts across its width: at its right where its columns run from the right
  private val gridLeft: Val[Double] = own(Val(if (mirrored) width - scroll.size.width() else 0.0))
  // how far there is to scroll on each side: its start is its right end where it runs from the right
  private val beyond: Val[Double] = own(Val(math.max(0.0, across() - scroll.size.width())))
  private val fromRight: Val[Boolean] = own(Val(mirrored && Direction.rtl()))
  private val leftShade = shade(true)
  private val rightShade = shade(false)

  cells.indices.foreach { r =>
    val row = new Container {
      semantics @= Semantics("row")
      copyAs @= "tr"
      backgroundColor := (if (r == 0) look.header() else look.rowFill(r - 1))
    }
    row.position.top := top(r)
    row.size.width := across()
    row.size.height := heights(r)()
    (0 until columns).foreach { c =>
      val cell = cells(r)(c)
      val align = table.align(c)
      // the end of its room is its left where the columns run from the right
      val end = if (mirrored) TextAlign.Start else TextAlign.End
      cell.position.left := left(c) + pad + (if (align == TextAlign.Center) (inner(c) - cell.size.width()) / 2.0
        else if (align == end) inner(c) - cell.size.width() else 0.0)
      cell.position.top @= look.vertical(r == 0)
      row.children += cell
    }
    // where the look rules the cells, a rule under every row but the last
    if (r > 0 && look.rules) grid.children += rule(true, r)
    grid.children += row
  }
  if (look.rules) (1 until columns).foreach(c => grid.children += rule(false, c))
  grid.children += edge
  grid.size.width := across()
  grid.size.height := down()
  TableLook.clip(grid, edge)
  scroll.content.children += grid
  scroll.size.width := math.min(width, across())
  // as tall as its rows, and the scrollbar under them where the page draws one
  scroll.size.height := down() + scroll.scrollbarRoom()
  scroll.position.left := gridLeft()
  scroll.focusable := across() > width
  size.width := width
  size.height := scroll.size.height()
  children ++= List(twins, scroll, leftShade, rightShade)

  // where column `c` starts, from the table's left: from its right where the columns run from the right
  private def left(c: Int): Double = {
    val before = (0 until c).foldLeft(0.0)((x, i) => x + widths()(i))
    if (mirrored) across() - before - widths()(c) else before
  }
  private def top(r: Int): Double = (0 until r).foldLeft(0.0)((y, i) => y + heights(i)())
  // the room for column `c`'s words
  private def inner(c: Int): Double = if (c < widths().size) widths()(c) - pad * 2.0 else 0.0

  // each column's widest, of `twins`, with the cells' padding: a whole pixel more, so a cell given it never wraps
  private def widest(twins: Vector[Vector[RichText]]): Vector[Double] = (0 until columns).toVector.map { c =>
    twins.foldLeft(0.0)((w, row) => if (c < row.size) math.max(w, math.ceil(row(c).size.width())) else w) + pad * 2.0
  }

  /** A rule across above row `at`, or down before column `at`. */
  private def rule(across: Boolean, at: Int): Container = new Container {
    backgroundColor := Theme.border()
    if (across) {
      position.top := top(at)
      size.width := TableView.this.across()
      size.height @= 1.0
    }
    else {
      position.left := (if (mirrored) left(at) + widths()(at) - 1.0 else left(at))
      size.width @= 1.0
      size.height := down()
    }
  }

  /** The shade over its `leftSide` edge, there while the table goes on past that edge. A finger, a click and the
    * wheel go through it to the table. */
  private def shade(leftSide: Boolean): Container = new Container {
    gradient := {
      val tint = Theme.text().withAlpha(0.18)
      val gone = tint.withAlpha(0.0)
      Some(Gradient(90.0, if (leftSide) List(GradientStop(tint, 0.0), GradientStop(gone, 1.0))
      else List(GradientStop(gone, 0.0), GradientStop(tint, 1.0))))
    }
    size.width @= TableView.shadeWidth
    size.height := down()
    position.left := gridLeft() + (if (leftSide) 0.0 else scroll.size.width() - TableView.shadeWidth)
    // the start shows more once scrolled from it, the end while it has not reached it
    visible := {
      val atStart = leftSide != fromRight()
      if (atStart) scroll.offset() > 0.5 else scroll.offset() < beyond() - 0.5
    }
    Gestures.passThrough(node)
  }

  // a cell drawn: its words on one line where its column has room, else wrapped to the column
  private def cell(shown: List[TextRun], r: Int, c: Int): RichText = new RichText {
    font.size @= points
    color := (if (ctx.quiet) Theme.muted() else Theme.text())
    follow = href => ctx.press(href, this)
    semantics @= Semantics(if (r == 0) "columnheader" else "cell")
    copyAs @= (if (r == 0) "th" else "td")
    direction @= ctx.direction
    size.width := math.min(inner(c), most()(c) - pad * 2.0)
    // its lines, where it wraps, aligned as the column
    align @= table.align(c)
    runs @= shown
  }

  // a cell measured, on one line or a word to a line
  private def twin(shown: List[TextRun], words: Boolean): RichText = {
    val text = new RichText {
      whiteSpace @= (if (words) WhiteSpace.Pre else WhiteSpace.NoWrap)
      font.size @= points
      direction @= ctx.direction
      runs @= (if (words) shown.map(run => run.copy(text = TableView.broken(run.text))) else shown)
    }
    twins.children += text
    text
  }

  override def kind: String = "TableView"
}

object TableView {
  /** How wide a shade is at an edge the table goes on past. */
  val shadeWidth: Double = 18.0

  /** `text` with a line break for each space or tab, so that measured it is as wide as its widest word. */
  def broken(text: String): String = text.replace(' ', '\n').replace('\t', '\n')
}
