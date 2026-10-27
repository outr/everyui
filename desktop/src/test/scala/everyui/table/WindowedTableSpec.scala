package everyui.table

import everyui.async.Task
import everyui.component.{Component, Container, ScrollContainer}
import everyui.focus.Focus
import everyui.kit.SearchField
import everyui.paging.{Page, PageRequest, PagedSource, Paging}
import everyui.render.FileAccess
import everyui.{FxSpec, ui}

import java.nio.file.{Files => NioFiles}

/** A `DataTable` that makes only the rows that show: a long table of its own rows (past `virtualAfter`, its rows
  * scrolling), its filter, sort, copies and exports still over every row; and a table of a server's rows
  * (`DataTable.paged`), placeholders until a page comes, searched and sorted by the server where it can be, its copies
  * the rows that have come and its export every row matching, read a page at a time into the file chosen, stopped on
  * the way. A column not exported is left out of both. */
class WindowedTableSpec extends FxSpec {
  private val dir = NioFiles.createTempDirectory("everyui-windowed-spec")

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
  // the rows drawn, in their order, as their first cells' words
  private def drawn(t: DataTable): Vector[String] = body(t).children().collect { case r: DataRow => r.content.head.text }
  private def greyed(t: DataTable): Int = body(t).children().count(c => !c.isInstanceOf[DataRow])
  private def under[T](c: Component)(using tag: scala.reflect.ClassTag[T]): Vector[T] = {
    var found = Vector.empty[T]
    var pending = List(c)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = at.childComponents.toList ::: pending.tail
      at match {
        case x: T => found = found :+ x
        case _ => ()
      }
    }
    found
  }

  private def long(n: Int): DataTable = fx {
    val t = DataTable(Vector(DataColumn("Name"), DataColumn("Count"), DataColumn("Row", exported = false)),
      (0 until n).toVector.map(i => Vector("row " + i.toString, (i * 7 % 1000).toString, i.toString)))
    t.maxHeight @= 300.0
    t
  }

  /** A server of `total` rows of a name and a count: answering at once, or holding each answer until the spec gives it.
    * The query keeps the rows whose name holds it; the sort, by "name", turns them round. */
  class Server(total: Int, held: Boolean = false) {
    var asked: Vector[PageRequest] = Vector.empty
    var owed: Vector[() => Unit] = Vector.empty

    def fetch(request: PageRequest): Task[Either[String, Page[Vector[DataCell]]]] = Task.async { done =>
      asked = asked :+ request
      val all = (0 until total).filter(i => ("item " + i.toString).contains(request.query))
      val ordered = if (request.sort.exists(!_.ascending)) all.reverse else all
      val rows = ordered.slice(request.offset, request.offset + request.limit).toVector
        .map(i => Vector(DataCell("item " + i.toString), DataCell((i % 10).toString)))
      val answer = () => done(Right(Page(rows, Some(all.size))))
      if (held) owed = owed :+ answer else answer()
    }

    def answerAll(): Unit = while (owed.nonEmpty) {
      val next = owed.head
      owed = owed.tail
      next()
    }
  }

  private def served(server: Server, paging: Paging = Paging.Anywhere): (PagedSource[Vector[DataCell]], DataTable) = fx {
    val source = new PagedSource[Vector[DataCell]](_.head.text, pageSize = 50, paging = paging)(server.fetch)
    val t = DataTable.paged(Vector(DataColumn("Name", key = "name"), DataColumn("Digit")), source)
    t.maxHeight @= 300.0
    (source, t)
  }

  "A long table" should {
    "make only the rows that show, and the rest as it scrolls, its filter and sort over every row" in {
      val t = long(5000)
      onScreen(t) {
        fx(t.windowed()) shouldBe true
        val first = fx(drawn(t))
        first.head shouldBe "row 0"
        first.size should be < 60
        // scrolled far down: the rows there, and none of the first
        fx(rowsScroll(t).scrollTo(60000.0))
        eventually(drawn(t).exists(_ != "row 0") && !drawn(t).contains("row 0")) shouldBe true
        fx(drawn(t).size) should be < 60
        // the filter finds a row however far down it is
        fx(t.filter @= "row 4321")
        eventually(t.shown().size == 1) shouldBe true
        fx(rowsScroll(t).scrollTo(0.0))
        eventually(drawn(t) == Vector("row 4321")) shouldBe true
        fx(t.filter @= "")
        // sorted down by its name: the last row first
        fx {
          t.sortBy(0)
          t.sortBy(0)
        }
        // (numbers in words by their value)
        eventually(drawn(t).headOption.contains("row 4999")) shouldBe true
        // a copy is of every row, less the column not exported
        val text = fx(t.tsv())
        text.linesIterator.size shouldBe 5001
        text.linesIterator.next() shouldBe "Name\tCount"
      }
    }
    "be drawn as before where it is short, or as tall as its rows" in {
      val short = long(50)
      onScreen(short) {
        fx(short.windowed()) shouldBe false
        fx(drawn(short).size) shouldBe 50
      }
      val tall = long(1000)
      fx(tall.maxHeight @= 0.0)
      onScreen(tall) {
        fx(tall.windowed()) shouldBe false
      }
    }
  }

  "A very long Markdown table" should {
    "scroll within its own height, making only the rows that show, and turn to it as it streams past the count" in {
      def table(n: Int): String = "| Step | Does |\n|---|---|\n" + (1 to n).map(i => "| Step " + i.toString + " | Does " + i.toString + " |").mkString("\n")
      val view = fx(everyui.markdown.MarkdownView(table(20), 700.0))
      onScreen(view) {
        eventually(view.children().nonEmpty) shouldBe true
        val wrapper = fx(view.children().head.asInstanceOf[everyui.markdown.MarkdownTable])
        val t = fx(wrapper.rich.get)
        fx(t.windowed()) shouldBe false
        fx(t.maxHeight()) shouldBe 0.0
        // streamed on past the count: its own height, and only the rows that show made
        fx(view.content @= table(3000))
        eventually(t.windowed()) shouldBe true
        fx(t.maxHeight()) shouldBe everyui.markdown.MarkdownTable.longHeight
        eventually(drawn(t).headOption.contains("Step 1")) shouldBe true
        fx(drawn(t).size) should be < 60
        fx(t.csv().split("\r\n").length) shouldBe 3001
      }
    }
  }

  "A table of a server's rows" should {
    "draw placeholders until a page comes, then its rows, asking only for what is near" in {
      val server = new Server(100000, held = true)
      val (source, t) = served(server)
      onScreen(t) {
        fx(greyed(t)) should be > 0
        fx(drawn(t)) shouldBe Vector.empty
        fx(server.answerAll())
        eventually(drawn(t).headOption.contains("item 0")) shouldBe true
        fx(server.asked.map(_.offset).max) should be < 1000
        fx(rowsScroll(t).scrollTo(2000.0 * DataTable.lineHeight(t.style)))
        eventually(server.asked.exists(_.offset >= 1900)) shouldBe true
        fx(server.answerAll())
        eventually(drawn(t).exists(_.startsWith("item 19"))) shouldBe true
        fx(source.count) shouldBe 100000
      }
    }
    "offer its filter and sort only where the source can, and send them to it" in {
      val server = new Server(500)
      val (source, t) = served(server)
      onScreen(t) {
        fx(under[SearchField](t)) shouldBe Vector.empty
        // a heading it cannot sort by does nothing
        fx(t.sortBy(0))
        fx(t.sort()) shouldBe None
        fx {
          source.searchable @= true
          source.sortable @= Set("name")
        }
        fx(under[SearchField](t).size) shouldBe 1
        fx(t.sortBy(1))
        fx(t.sort()) shouldBe None
        fx(t.sortBy(0))
        fx(source.sort()) shouldBe Some(everyui.table.Sorting("name"))
        fx(t.sortBy(0))
        eventually(drawn(t).headOption.contains("item 499")) shouldBe true
        // the words go to the source a beat after the last key
        fx(t.filter @= "item 42")
        eventually(source.query() == "item 42") shouldBe true
        eventually(server.asked.last.query == "item 42") shouldBe true
        eventually(drawn(t).headOption.contains("item 429")) shouldBe true
      }
    }
    "copy the rows that have come, and export every row matching a page at a time, or stop on the way" in {
      val server = new Server(1234)
      val (source, t) = served(server, Paging.InOrder)
      onScreen(t) {
        eventually(drawn(t).nonEmpty) shouldBe true
        val loaded = fx(t.loaded.size)
        loaded should (be > 0 and be < 1234)
        fx(t.tsv().linesIterator.size) shouldBe loaded + 1
        // every row, into the file chosen, never asked of the view
        val target = dir.resolve("all.csv").toFile
        FileAccess.chooseSave = _ => Some(target)
        fx(t.exportCsv())
        eventually(t.exporting().isEmpty && target.exists) shouldBe true
        val lines = new String(NioFiles.readAllBytes(target.toPath), "UTF-8").split("\r\n").toVector
        lines.size shouldBe 1235
        lines.head shouldBe DataTable.byteOrderMark + "Name,Digit"
        lines.last shouldBe "item 1233,3"
        // stopped on the way: the file let go
        val stopped = dir.resolve("stopped.csv").toFile
        FileAccess.chooseSave = _ => Some(stopped)
        fx {
          t.exportCsv()
          t.stopExporting()
        }
        eventually(t.exporting().isEmpty) shouldBe true
        stopped.exists shouldBe false
      }
    }
  }
}
