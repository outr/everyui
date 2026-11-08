package everyui.table

import everyui.clipboard.Clipboard
import everyui.component.{Component, Container, RichText, ScrollContainer}
import everyui.event.{Key, KeyEvent, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.kit.SearchField
import everyui.markdown.{MarkdownTable, MarkdownView, TableView}
import everyui.{FxSpec, ui}

/** A `DataTable` on the desktop's nodes: sorted by its headers (numbers, dates, words, back as they came), filtered
  * with a count, chosen by clicks and keys and copied; its header kept as its rows scroll within its tallest; what a
  * screen reader is told; a long table made a page at a time; and a Markdown table drawn as one, looking at rest as the
  * plain table did, unless the view says not to. */
class DataTableSpec extends FxSpec {
  private var kept: Option[java.util.Map[javafx.scene.input.DataFormat, AnyRef]] = None

  override def beforeAll(): Unit = {
    super.beforeAll()
    kept = Some(fx {
      val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
      val all = new java.util.HashMap[javafx.scene.input.DataFormat, AnyRef]()
      clipboard.getContentTypes.forEach(t => Option(clipboard.getContent(t)).foreach(c => all.put(t, c)))
      all
    })
  }

  override def afterAll(): Unit = {
    kept.foreach(all => fx(javafx.scene.input.Clipboard.getSystemClipboard.setContent(all)))
    super.afterAll()
  }

  private val columns = Vector(DataColumn("Persona"), DataColumn("Conversations"), DataColumn("Last active"),
    DataColumn("Spend"))
  private val rows = Vector(
    Vector("Grace", "1,204", "Mar 3, 2026", "$1,299.00"),
    Vector("ada", "87", "2025-12-28", "—"),
    Vector("Alan", "12,000", "9 October 2026", "$12.50"),
    Vector("Barbara", "", "2026-06-30", "$999"),
    Vector("Edsger", "3", "1/2/2024", "$0.80"))

  private def onScreen[T](view: Component, width: Double = 800.0)(test: => T): T = {
    fx {
      ui.children.clear()
      view.size.width @= width
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
  private def rowsScroll(t: DataTable): ScrollContainer = grid(t).children().collectFirst { case s: ScrollContainer => s }.get
  private def body(t: DataTable): Container = rowsScroll(t).content.children().head.asInstanceOf[Container]
  // the rows showing, in their order, as their first cells' words
  private def names(t: DataTable): Vector[String] =
    body(t).children().collect { case r: DataRow if r.shown() => r.content.head.text }
  private def click(t: DataTable, cell: Component, shift: Boolean = false, ctrl: Boolean = false): Unit =
    grid(t).event.click @= PointerEvent(PointerKind.Click, cell.absoluteLeft + 2.0, cell.absoluteTop + 2.0, ctrl = ctrl,
      shift = shift)
  private def row(t: DataTable, name: String): DataRow =
    body(t).children().collectFirst { case r: DataRow if r.content.head.text == name => r }.get
  private def key(t: DataTable, k: Key, name: String, ctrl: Boolean = false, shift: Boolean = false): Unit =
    grid(t).event.key.down @= KeyEvent(k, name, ctrl = ctrl, shift = shift)
  private def clipboard(): Option[String] = {
    var answer: Option[Option[String]] = None
    fx(Clipboard.read().map(t => answer = Some(t)).start())
    eventually(answer.nonEmpty) shouldBe true
    answer.get
  }

  "A data table" should {
    "sort by a header's click, numbers by value, dates by when, words as the language orders them, then back" in {
      val t = fx(DataTable(columns, rows))
      onScreen(t) {
        eventually(names(t).size == 5 && head(t).size.height() > 0.0) shouldBe true
        fx(t.kindOf(1)) shouldBe ColumnKind.Number
        fx(t.kindOf(2)) shouldBe ColumnKind.Date
        fx(t.kindOf(3)) shouldBe ColumnKind.Number
        fx(click(t, head(t).cells(1)))
        fx(names(t)) shouldBe Vector("Edsger", "ada", "Grace", "Alan", "Barbara")
        fx(click(t, head(t).cells(1)))
        fx(names(t)) shouldBe Vector("Alan", "Grace", "ada", "Edsger", "Barbara")
        fx(click(t, head(t).cells(1)))
        fx(t.sort()) shouldBe None
        fx(names(t)) shouldBe Vector("Grace", "ada", "Alan", "Barbara", "Edsger")
        fx(click(t, head(t).cells(2)))
        fx(names(t)) shouldBe Vector("Edsger", "ada", "Grace", "Barbara", "Alan")
        fx(click(t, head(t).cells(3)))
        fx(names(t)) shouldBe Vector("Edsger", "Alan", "Barbara", "Grace", "ada")
        fx(click(t, head(t).cells(0)))
        fx(names(t)) shouldBe Vector("ada", "Alan", "Barbara", "Edsger", "Grace")
        // Enter on a header sorts too, the other way
        fx(key(t, Key.Enter, "Enter"))
        fx(names(t)) shouldBe Vector("Grace", "Edsger", "Barbara", "Alan", "ada")
        // a screen reader hears which way, on that header alone
        fx(head(t).cells.map(_.semantics().sort)) shouldBe Vector("descending", "", "", "")
        fx(head(t).cells.map(_.semantics().role).distinct) shouldBe Vector("columnheader")
        fx(grid(t).semantics().role) shouldBe "table"
        fx(row(t, "ada").semantics().role) shouldBe "row"
        fx(row(t, "ada").cells.map(_.semantics().role).distinct) shouldBe Vector("cell")
        // numbers stand at the end of their cells, words at the start
        fx(t.aligns()) shouldBe Vector(everyui.component.types.TextAlign.Start, everyui.component.types.TextAlign.End,
          everyui.component.types.TextAlign.Start, everyui.component.types.TextAlign.End)
      }
    }
    "offer its filter only past filterAfter rows, hide the rows without every word typed, and count those shown" in {
      val small = fx(DataTable(columns, rows))
      onScreen(small) {
        eventually(names(small).size == 5) shouldBe true
        fx(small.children().exists {
          case c: Container => c.children().exists(_.isInstanceOf[SearchField])
          case _ => false
        }) shouldBe false
      }
      val many = fx(DataTable(columns, (0 until 12).toVector.map(i => rows(i % 5).updated(0, rows(i % 5).head + " " + i))))
      onScreen(many) {
        eventually(names(many).size == 12) shouldBe true
        val field = fx(many.children().collectFirst { case c: Container if c.children().exists(_.isInstanceOf[SearchField]) =>
          c.children().collectFirst { case s: SearchField => s }.get }.get)
        fx(field.value @= "GRACE")
        fx(names(many)) shouldBe Vector("Grace 0", "Grace 5", "Grace 10")
        fx(many.shown().size) shouldBe 3
        val count = fx(field.parent().get.asInstanceOf[Container].children().collectFirst {
          case v: everyui.component.TextView => v.content() }.get)
        count shouldBe "3 of 12"
        // every word, in any cell
        fx(field.value @= "ada 2025")
        fx(names(many)) shouldBe Vector("ada 1", "ada 6", "ada 11")
        fx(field.value @= "")
        fx(names(many).size) shouldBe 12
        fx(field.parent().get.asInstanceOf[Container].children().collectFirst {
          case v: everyui.component.TextView => v.content() }.get) shouldBe "12 rows"
        // the hidden rows take no room: the rows are as tall as those showing
        fx(field.value @= "Alan")
        eventually(body(many).size.height() < head(many).size.height() * 5.0) shouldBe true
      }
    }
    "choose rows by a click, Shift and Ctrl, and copy them, or the rows showing, as tab-separated lines" in {
      val t = fx(DataTable(columns, rows))
      onScreen(t) {
        eventually(names(t).size == 5 && row(t, "Alan").size.height() > 0.0) shouldBe true
        fx(click(t, row(t, "ada").cells(1)))
        fx(t.selection()) shouldBe Set(1)
        fx(row(t, "ada").semantics().selected) shouldBe Some(true)
        fx(click(t, row(t, "Barbara").cells(0), shift = true))
        fx(t.selection()) shouldBe Set(1, 2, 3)
        fx(click(t, row(t, "Alan").cells(0), ctrl = true))
        fx(t.selection()) shouldBe Set(1, 3)
        fx(key(t, Key.Other, "c", ctrl = true))
        clipboard() shouldBe Some("Persona\tConversations\tLast active\tSpend\nada\t87\t2025-12-28\t—\n" +
          "Barbara\t\t2026-06-30\t$999")
        // a plain click on the one row chosen lets it go; Escape lets them all go
        fx(click(t, row(t, "Grace").cells(0)))
        fx(t.selection()) shouldBe Set(0)
        fx(click(t, row(t, "Grace").cells(0)))
        fx(t.selection()) shouldBe Set.empty[Int]
        fx(t.copy())
        clipboard() shouldBe Some(fx(t.tsv()))
        fx(t.tsv()).linesIterator.size shouldBe 6
        fx(t.filter @= "a")
        fx(t.copyMarkdown())
        clipboard() shouldBe Some(fx(t.markdown()))
        fx(t.markdown()).linesIterator.toList.take(2) shouldBe List("| Persona | Conversations | Last active | Spend |",
          "|---|---:|---|---:|")
      }
    }
    "draw a cell holding no value as a muted NULL, read it as null, and copy and export it blank" in {
      val held = Vector(
        Vector(DataCell("Grace"), DataCell.Null, DataCell("Mar 3, 2026"), DataCell("$1,299.00")),
        Vector(DataCell("ada"), DataCell("87"), DataCell.of(None), DataCell.of(Some("—"))))
      val t = fx(new DataTable(columns, held))
      onScreen(t) {
        eventually(names(t).size == 2 && row(t, "Grace").size.height() > 0.0) shouldBe true
        val nothing = fx(row(t, "Grace").cells(1))
        fx(nothing.runs().map(_.text)) shouldBe List("NULL")
        fx(nothing.color()) shouldBe fx(everyui.theme.Theme.muted())
        fx(nothing.semantics()) shouldBe everyui.component.types.Semantics("cell", label = "null")
        // a cell with words, even a dash, is as it was
        val dash = fx(row(t, "ada").cells(3))
        fx(dash.runs().map(_.text)) shouldBe List("—")
        fx(dash.color()) shouldBe fx(everyui.theme.Theme.text())
        fx(dash.semantics()) shouldBe everyui.component.types.Semantics("cell")
        fx(row(t, "ada").cells(2).runs().map(_.text)) shouldBe List("NULL")
        // blank in every copy and export
        fx(t.rowTexts(0)) shouldBe Vector("Grace", "", "Mar 3, 2026", "$1,299.00")
        fx(t.tsv()) shouldBe "Persona\tConversations\tLast active\tSpend\nGrace\t\tMar 3, 2026\t$1,299.00\n" +
          "ada\t87\t\t—"
        fx(t.csv()) shouldBe "Persona,Conversations,Last active,Spend\r\nGrace,,\"Mar 3, 2026\",\"$1,299.00\"\r\n" +
          "ada,87,,—\r\n"
        fx(t.markdown()).linesIterator.toList.drop(2) shouldBe List("| Grace |  | Mar 3, 2026 | $1,299.00 |",
          "| ada | 87 |  | — |")
        fx(t.copy())
        clipboard() shouldBe Some(fx(t.tsv()))
      }
    }
    "move between its cells with the keys, choose rows as it goes, and copy the cell it is on" in {
      val t = fx(DataTable(columns, rows))
      onScreen(t) {
        eventually(names(t).size == 5) shouldBe true
        fx(grid(t).focus())
        // the focus layer leaves it the arrows it can move by, and not one that would take it out
        fx(grid(t).keepsEvent(KeyEvent(Key.ArrowDown, "ArrowDown"))) shouldBe true
        fx(grid(t).keepsEvent(KeyEvent(Key.ArrowUp, "ArrowUp"))) shouldBe false
        fx(grid(t).keepsEvent(KeyEvent(Key.Tab, "Tab"))) shouldBe false
        fx(Focus.key("ArrowDown", true))
        fx(Focus.key("ArrowDown", false))
        fx(Focus.key("ArrowRight", true))
        fx(Focus.key("ArrowRight", false))
        fx(key(t, Key.Other, "c", ctrl = true))
        clipboard() shouldBe Some("1,204")
        fx(key(t, Key.ArrowDown, "ArrowDown", shift = true))
        fx(key(t, Key.ArrowDown, "ArrowDown", shift = true))
        fx(t.selection()) shouldBe Set(0, 1, 2)
        fx(key(t, Key.Escape, "Escape"))
        fx(t.selection()) shouldBe Set.empty[Int]
        fx(key(t, Key.Space, " "))
        fx(t.selection()) shouldBe Set(2)
        fx(key(t, Key.Other, "a", ctrl = true))
        fx(t.selection()) shouldBe Set(0, 1, 2, 3, 4)
        fx(Focus.focused().exists(_ eq grid(t))) shouldBe true
      }
    }
    "keep its header in place as its rows scroll within its tallest" in {
      val t = fx(DataTable(columns, (0 until 40).toVector.map(i => rows(i % 5))))
      fx(t.maxHeight @= 300.0)
      onScreen(t) {
        eventually(names(t).size == 40 && t.size.height() > 0.0) shouldBe true
        val r = fx(rowsScroll(t))
        fx(grid(t).size.height()) shouldBe 300.0 +- 1.0
        fx(body(t).size.height()) should be > 600.0
        fx(r.size.height()) shouldBe (300.0 - head(t).size.height()) +- 1.0
        fx(r.position.top()) shouldBe head(t).size.height()
        fx(r.scrollTo(200.0))
        eventually(r.offset() > 150.0) shouldBe true
        fx(head(t).position.top()) shouldBe 0.0
        fx(head(t).absoluteTop) shouldBe fx(grid(t).absoluteTop) +- 0.5
        fx(t.maxHeight @= 0.0)
        eventually(grid(t).size.height() > 600.0) shouldBe true
      }
    }
    "make a long table a page at a time, measuring its widest cells" in {
      val t = fx(DataTable(columns, (0 until 300).toVector.map(i => rows(i % 5).updated(0, rows(i % 5).head + " " + i))))
      onScreen(t) {
        fx(body(t).children().size) should be < 300
        eventually(body(t).children().size == 300) shouldBe true
        fx(t.sortBy(1))
        fx(names(t).take(3)) shouldBe Vector("Edsger 4", "Edsger 9", "Edsger 14")
      }
    }
  }

  "A Markdown table" should {
    val prose = "| Step | What it does | Where it fails |\n|---|---|---|\n" +
      "| Parse | Reads the request and checks its fields | A field missing or of the wrong type |\n" +
      "| Load | Fetches the settings | The store times out under load |"
    val path = "| File | Notes |\n|---|---|\n| `shared/src/main/scala/everyui/markdown/TableView.scala` | Where tables go |"

    def r(v: Double): Double = math.round(v * 100.0) / 100.0
    // each cell's words, where they are on the screen
    def boxes(view: MarkdownView): Vector[(Double, Double, Double, Double)] = {
      def all(c: Component): Vector[RichText] = c match {
        case t: RichText => Vector(t)
        case k: Container => k.children().filter(_.visible()).flatMap(all)
        case _ => Vector.empty
      }
      all(view.children().head).filter(_.semantics().role.nonEmpty)
        .map(t => (r(t.absoluteLeft), r(t.absoluteTop), r(t.size.width()), r(t.size.height()))).sortBy(b => (b._2, b._1))
    }

    "be the plain table at rest, and once wanted a data table laid out alike: wide, narrow, and scrolling" in {
      for ((text, width) <- List((prose, 1000.0), (prose, 300.0), (path, 330.0), ("| a | b |\n|:-:|--:|\n| 1 | 2 |", 500.0))) {
        val rich = fx(MarkdownView(text, width))
        val plain = fx(MarkdownView(text, width))
        fx(plain.richTables @= false)
        val (rest, engaged) = onScreen(rich, width) {
          eventually(rich.size.height() > 0.0 && boxes(rich).forall(_._4 > 0.0)) shouldBe true
          val table = fx(rich.children().head.asInstanceOf[MarkdownTable])
          fx(table.rich) shouldBe None
          val rest = (fx(boxes(rich)), fx(r(rich.size.height())))
          // the focus (or the pointer) wants it: a data table in its place
          fx(table.focus())
          fx(table.rich.nonEmpty) shouldBe true
          fx(Focus.focused().exists(f => Focus.within(f, table.rich.get))) shouldBe true
          eventually(boxes(rich).nonEmpty && boxes(rich).forall(_._4 > 0.0) && boxes(rich) == rest._1) shouldBe true
          (rest, (fx(boxes(rich)), fx(r(rich.size.height()))))
        }
        val before = onScreen(plain, width) {
          eventually(plain.size.height() > 0.0 && boxes(plain).forall(_._4 > 0.0)) shouldBe true
          fx(plain.children().head.isInstanceOf[TableView]) shouldBe true
          (fx(boxes(plain)), fx(r(plain.size.height())))
        }
        rest shouldBe before
        engaged shouldBe before
      }
    }
    "be a data table at once where its filter shows at rest" in {
      val long = prose + "\n" + (1 to 9).map(i => "| Step " + i + " | Does " + i + " | Fails " + i + " |").mkString("\n")
      val view = fx(MarkdownView(long, 700.0))
      onScreen(view, 700.0) {
        eventually(view.children().nonEmpty) shouldBe true
        fx(view.children().head.asInstanceOf[MarkdownTable].rich.nonEmpty) shouldBe true
      }
    }
    "follow the view's richTables, drawn again plain and rich as it changes" in {
      val view = fx(MarkdownView(prose, 600.0))
      onScreen(view, 600.0) {
        eventually(view.children().nonEmpty) shouldBe true
        fx(view.children().head.getClass.getSimpleName) shouldBe "MarkdownTable"
        fx(view.richTables @= false)
        fx(view.children().head.getClass.getSimpleName) shouldBe "TableView"
        fx(view.richTables @= true)
        fx(view.children().head.getClass.getSimpleName) shouldBe "MarkdownTable"
      }
    }
    "take its rows in place as an answer streams in, those that did not change keeping what draws them" in {
      val view = fx(MarkdownView(prose, 600.0))
      onScreen(view, 600.0) {
        eventually(view.children().nonEmpty) shouldBe true
        val wrapper = fx(view.children().head.asInstanceOf[MarkdownTable])
        fx(wrapper.engage())
        val table = fx(wrapper.rich.get)
        val first = fx(row(table, "Parse"))
        fx(view.content @= prose + "\n| Render | Lays the page out |")
        fx(view.children().head eq wrapper) shouldBe true
        fx(wrapper.rich.get eq table) shouldBe true
        fx(names(table)) shouldBe Vector("Parse", "Load", "Render")
        fx(row(table, "Parse") eq first) shouldBe true
      }
    }
  }
}
