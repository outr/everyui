package everyui.table

import everyui.Color
import everyui.component.{Component, Container, RichText, ScrollContainer}
import everyui.component.types.TextAlign
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.markdown.{MarkdownTable, MarkdownView, TableLook, TableView}
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, ui}

/** How a table is drawn (`TableLook`) on the desktop's nodes: its defaults; its header band and every second row's
  * stripe in the theme's colours, following a change of palette and a palette's extras; the stripes by where a row
  * shows, sorted; rounded and cut to its corners, its edge over it and no rules unless the look rules it; a Markdown
  * table's alignment row honoured, mirrored right to left; and the plain table and the `DataTable` made in its place
  * drawn alike. */
class TableLookSpec extends FxSpec {
  private val spend = "| Provider | Region | Spend |\n|:--|:-:|--:|\n| Anvil | us-east | 1,204.50 |\n" +
    "| Brightfold | eu-west | 87.00 |\n| Cobalt | ap-south | 12,000.00 |\n| Dune | us-west | 3.25 |"

  private def hex(c: Color): String = c.toHexAlpha

  private def onScreen[T](view: Component, width: Double = 600.0)(test: => T): T = {
    fx {
      ui.children.clear()
      view.size.width @= width
      ui.children += view
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
      Theme.palette @= Palette.dark
      Direction.set(false)
    }
  }

  private def scroll(c: Container): ScrollContainer = c.children().collectFirst { case s: ScrollContainer => s }.get
  private def grid(c: Container): Container = scroll(c).content.children().head.asInstanceOf[Container]
  // the plain table's rows, header first, and their cells
  private def plainRows(t: TableView): Vector[Container] = grid(t).children().collect {
    case row: Container if row.children().exists(_.isInstanceOf[RichText]) => row
  }
  private def cells(row: Container): Vector[RichText] = row.children().collect { case t: RichText => t }
  // the data table's rows, header first, the body's in the order they show
  private def richRows(t: DataTable): Vector[DataRow] = {
    val head = grid(t).children().collectFirst { case r: DataRow => r }.get
    val body = grid(t).children().collectFirst { case s: ScrollContainer => s }.get.content.children().head
      .asInstanceOf[Container]
    head +: body.children().collect { case r: DataRow if r.shown() => r }
  }
  private def plain(view: MarkdownView): TableView = view.children().head.asInstanceOf[TableView]
  private def rich(view: MarkdownView): DataTable = {
    val wrapper = view.children().head.asInstanceOf[MarkdownTable]
    wrapper.engage()
    wrapper.rich.get
  }
  "A table's look" should {
    "be striped, rounded, banded and roomier by default, everywhere a table is drawn" in {
      val look = TableLook()
      look.zebra shouldBe true
      look.radius shouldBe 8.0
      look.rules shouldBe false
      (look.across, look.down, look.headerDown, look.headerWeight) shouldBe ((12.0, 8.0, 10.0, 600))
      DataTableStyle().look shouldBe look
      MarkdownView.tableLook.get shouldBe look
      fx(new MarkdownView).tableLook.get shouldBe look
      TableLook.ruled.rules shouldBe true
      TableLook.ruled.zebra shouldBe false
      TableLook.ruled.radius shouldBe 0.0
    }
    "take its colours from the theme: the accent's band, the text's stripe, a palette's extras over both" in {
      val look = TableLook()
      fx(Theme.palette @= Palette.dark)
      fx(hex(look.header())) shouldBe hex(Palette.dark.accent.withAlpha(0.1))
      fx(hex(look.rowFill(0))) shouldBe hex(Color.Clear)
      fx(hex(look.rowFill(1))) shouldBe hex(Palette.dark.text.withAlpha(0.04))
      fx(hex(look.rowFill(2))) shouldBe hex(Color.Clear)
      fx(Theme.palette @= Palette.light)
      fx(hex(look.header())) shouldBe hex(Palette.light.accent.withAlpha(0.1))
      fx(hex(look.rowFill(3))) shouldBe hex(Palette.light.text.withAlpha(0.04))
      val gold = Color.fromHex("c9a227")
      fx(Theme.palette @= Palette.dark.copy(extras = Map("table.header" -> gold, "table.stripe" -> gold.withAlpha(0.2))))
      fx(hex(look.header())) shouldBe hex(gold)
      fx(hex(look.rowFill(1))) shouldBe hex(gold.withAlpha(0.2))
      fx(Theme.palette @= Palette.dark)
      look.copy(zebra = false).rowFill(1) shouldBe Color.Clear
    }
    "band the header and stripe every second row of a Markdown table, rounded and cut to its corners, unruled" in {
      val view = fx(MarkdownView(spend, 600.0))
      fx(view.richTables @= false)
      onScreen(view) {
        eventually(view.children().nonEmpty && plainRows(plain(view)).forall(_.size.height() > 0.0)) shouldBe true
        val t = fx(plain(view))
        val rows = fx(plainRows(t))
        fx(rows.map(r => hex(r.backgroundColor()))) shouldBe Vector(hex(Palette.dark.accent.withAlpha(0.1)),
          hex(Color.Clear), hex(Palette.dark.text.withAlpha(0.04)), hex(Color.Clear), hex(Palette.dark.text.withAlpha(0.04)))
        // the header's words semibold, more room above and below them than a row's
        fx(cells(rows.head).head.runs().head.weight) shouldBe 600
        fx(cells(rows.head).head.position.top()) shouldBe 10.0
        fx(cells(rows(1)).head.position.top()) shouldBe 8.0
        fx(rows.head.size.height() - cells(rows.head).map(_.size.height()).max) shouldBe 20.0
        val g = fx(grid(t))
        fx(g.radius()) shouldBe 8.0
        fx(g.borderWidth()) shouldBe 0.0
        // the header band and the stripes are cut to the rounded corners here too, not to a square
        fx(g.node.fx.getClip match {
          case r: javafx.scene.shape.Rectangle => (r.getArcWidth, r.getArcHeight)
          case other => other
        }) shouldBe ((16.0, 16.0))
        val edge = fx(g.children().last)
        fx(edge.radius()) shouldBe 8.0
        fx(edge.borderWidth()) shouldBe 1.0
        fx((edge.size.width(), edge.size.height())) shouldBe fx((g.size.width(), g.size.height()))
        // no rules: the grid holds the rows and the edge alone
        fx(g.children().size) shouldBe rows.size + 1
        // a change of palette re-colours it
        fx(Theme.palette @= Palette.light)
        fx(hex(rows.head.backgroundColor())) shouldBe hex(Palette.light.accent.withAlpha(0.1))
        fx(hex(rows(2).backgroundColor())) shouldBe hex(Palette.light.text.withAlpha(0.04))
      }
    }
    "honour the alignment row: the first column at its start, the middle centred, the numbers at its end" in {
      for (richly <- List(false, true)) {
        val view = fx(MarkdownView(spend, 600.0))
        fx(view.richTables @= richly)
        onScreen(view) {
          eventually(view.children().nonEmpty && view.size.height() > 0.0) shouldBe true
          val rows: Vector[Container] = if (richly) fx(richRows(rich(view))) else fx(plainRows(plain(view)))
          eventually(rows.forall(_.size.height() > 0.0)) shouldBe true
          // each cell's words aligned within the room of its column, 12 pixels in from each side
          fx(rows.map(r => cells(r).map(_.align()))).distinct shouldBe
            Vector(Vector(TextAlign.Start, TextAlign.Center, TextAlign.End))
          val anvil = fx(cells(rows(1)))
          fx(anvil(0).position.left()) shouldBe (12.0 +- 0.5)
          fx(rows(1).size.width() - anvil(2).position.left() - anvil(2).size.width()) shouldBe (12.0 +- 0.5)
        }
      }
    }
    "mirror right to left: the first column at the right, the numbers at the left of theirs" in {
      for (richly <- List(false, true)) {
        fx(Direction.set(true))
        val view = fx(MarkdownView(spend, 600.0))
        fx(view.richTables @= richly)
        onScreen(view) {
          eventually(view.children().nonEmpty && view.size.height() > 0.0) shouldBe true
          val rows: Vector[Container] = if (richly) fx(richRows(rich(view))) else fx(plainRows(plain(view)))
          eventually(rows.forall(_.size.height() > 0.0)) shouldBe true
          val across = fx(rows.head.size.width())
          val anvil = fx(cells(rows(1)))
          // the provider's words end 12 pixels from the table's right edge, the spend starts 12 from its left
          fx(across - anvil(0).position.left() - anvil(0).size.width()) shouldBe (12.0 +- 0.5)
          fx(anvil(2).position.left()) shouldBe (12.0 +- 0.5)
          fx(anvil(1).position.left()) should be > fx(anvil(2).position.left() + anvil(2).size.width())
        }
      }
    }
    "stripe a data table's rows by where they show, sorted, and draw a chosen row in the selection's colour" in {
      val t = fx(DataTable(Vector(DataColumn("Name"), DataColumn("Count")),
        Vector(Vector("c", "3"), Vector("a", "1"), Vector("d", "4"), Vector("b", "2"))))
      onScreen(t) {
        eventually(richRows(t).size == 5 && richRows(t).forall(_.size.height() > 0.0)) shouldBe true
        val stripe = hex(Palette.dark.text.withAlpha(0.04))
        val clear = hex(Color.Clear)
        def fills: Vector[(String, String)] = richRows(t).tail.map(r => (r.content.head.text, hex(r.backgroundColor())))
        fx(fills) shouldBe Vector(("c", clear), ("a", stripe), ("d", clear), ("b", stripe))
        fx(hex(richRows(t).head.backgroundColor())) shouldBe hex(Palette.dark.accent.withAlpha(0.1))
        fx(t.sortBy(0))
        eventually(fills.map(_._1) == Vector("a", "b", "c", "d")) shouldBe true
        fx(fills) shouldBe Vector(("a", clear), ("b", stripe), ("c", clear), ("d", stripe))
        fx(t.selection @= Set(0))
        fx(richRows(t).tail.find(_.content.head.text == "c").map(r => hex(r.backgroundColor()))) should not be Some(clear)
        val g = fx(grid(t))
        fx(g.radius()) shouldBe 8.0
        fx(g.children().collect { case c: Container if c.borderWidth() == 1.0 => c.radius() }) shouldBe Vector(8.0)
      }
    }
    "draw the rules, square and unstriped, with the ruled look" in {
      val t = fx(new DataTable(Vector(DataColumn("Name"), DataColumn("Count")),
        Vector(Vector("a", "1"), Vector("b", "2")).map(_.map(DataCell(_))), DataTableStyle(look = TableLook.ruled)))
      onScreen(t) {
        eventually(richRows(t).size == 3 && richRows(t).forall(_.size.height() > 0.0)) shouldBe true
        val rows = fx(richRows(t))
        fx(hex(rows.head.backgroundColor())) shouldBe hex(Palette.dark.raised)
        fx(rows.tail.map(r => hex(r.backgroundColor()))) shouldBe Vector(hex(Color.Clear), hex(Color.Clear))
        // a rule above each body row, and one down between the columns in the header
        fx(rows.tail.map(_.children().count(c => !c.isInstanceOf[RichText]))) shouldBe Vector(1, 1)
        fx(rows.head.children().count(c => !c.isInstanceOf[RichText])) shouldBe 1
        fx(grid(t).radius()) shouldBe 0.0
      }
    }
    "be drawn alike at rest and once a data table: every row's fill, and the cells where they were" in {
      for (width <- List(600.0, 260.0)) {
        val view = fx(MarkdownView(spend, width))
        val other = fx(MarkdownView(spend, width))
        fx(other.richTables @= false)
        def boxes(rows: Vector[Container]): Vector[(String, Double, Double, Double)] = rows.flatMap { r =>
          cells(r).map(c => (hex(r.backgroundColor()), math.round(c.absoluteLeft * 10) / 10.0,
            math.round(c.absoluteTop * 10) / 10.0, math.round(r.size.height() * 10) / 10.0))
        }
        val before = onScreen(other, width) {
          eventually(other.children().nonEmpty && plainRows(plain(other)).forall(_.size.height() > 0.0)) shouldBe true
          fx(boxes(plainRows(plain(other))))
        }
        val after = onScreen(view, width) {
          eventually(view.children().nonEmpty) shouldBe true
          val t = fx(rich(view))
          eventually(richRows(t).size == 5 && richRows(t).forall(_.size.height() > 0.0) &&
            boxes(richRows(t)) == before) shouldBe true
          fx(boxes(richRows(t)))
        }
        after shouldBe before
      }
    }
  }
}
