package everyui.table

import everyui.async.Task
import everyui.component.{Modal, TextView}
import everyui.event.{Modifiers, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.menu.MenuItem
import everyui.paging.{Page, PageRequest, PagedSource}
import everyui.{Color, FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A table over a server's rows: placeholders until a page comes, a heading that sorts on the server, tick boxes and
  * every row chosen honestly, columns that fill the width and change at runtime, the row under the pointer and the
  * app's row colours, a cell edited through its menu, a footer, and a million rows scrolled with the memory bounded. */
class PagedTableSpec extends FxSpec {
  case class Entity(id: String, name: String, status: String)

  private val none = Modifiers(false, false, false, false)

  /** A server of `total` entities: answering at once, or holding each answer until the spec gives it. */
  class Server(total: Int, held: Boolean = false) {
    var asked: Vector[PageRequest] = Vector.empty
    var owed: Vector[() => Unit] = Vector.empty

    def fetch(request: PageRequest): Task[Either[String, Page[Entity]]] = Task.async { done =>
      asked = asked :+ request
      val desc = request.sort.exists(!_.ascending)
      val rows = (request.offset until math.min(total, request.offset + request.limit)).map { i =>
        val n = if (desc) total - 1 - i else i
        Entity("e" + n, "Entity " + n, if (n % 2 == 0) "Active" else "Inactive")
      }.toVector
      val answer = () => done(Right(Page(rows, Some(total))))
      if (held) owed = owed :+ answer else answer()
    }

    def answerAll(): Unit = while (owed.nonEmpty) {
      val next = owed.head
      owed = owed.tail
      next()
    }
  }

  private def table(server: Server, maxPages: Int = 20): (PagedSource[Entity], VirtualTable[Entity]) = fx {
    val source = new PagedSource[Entity](_.id, pageSize = 100, maxPages = maxPages)(server.fetch)
    val t = VirtualTable.paged[Entity](source, _.name, Vector(
      GridColumn.served[Entity]("name", "Name", 200.0)(_.name),
      GridColumn[Entity]("status", "Status", 140.0, () => new MenuCell[Entity](_.status,
        e => List("Active", "Inactive").map(s => MenuItem(s, () => source.replace(e.copy(status = s)),
          checked = Some(s == e.status))))),
      GridColumn.served[Entity]("id", "Id", 100.0, sortable = false)(_.id)))
    t.size.width @= 600.0
    t.size.height @= 400.0
    (source, t)
  }

  private def onScreen[T](t: VirtualTable[?])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += t
      t.focus()
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def press(code: KeyCode, name: String = "", ctrl: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, name, name, code, false, ctrl, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, name, name, code, false, ctrl, false, false))
  }

  private def row(t: VirtualTable[Entity], index: Int): TableRow[Entity] =
    t.list.rowAt(index).get.asInstanceOf[TableRow[Entity]]

  /** A click on row `index`, `along` from the table's start edge. */
  private def click(t: VirtualTable[Entity], index: Int, along: Double, held: Modifiers = none): Unit = fx {
    t.list.clickAt(t.list.absoluteLeft + along, t.list.absoluteTop + t.list.top(index) - t.list.offset() + 4.0, held)
  }

  "A paged table" should {
    "draw placeholders until a page comes, then its rows, and sort on the server by a heading" in {
      val server = new Server(1900000, held = true)
      val (source, t) = table(server)
      onScreen(t) {
        // before the first answer: a page of placeholders, a grey bar in each cell
        fx(t.list.count) shouldBe 100
        fx(row(t, 0).item()) shouldBe None
        fx(row(t, 0).children().count(c => c.visible() && c.size.height() == 10.0)) shouldBe 3
        fx(server.answerAll())
        fx(t.list.count) shouldBe 1900000
        fx(row(t, 0).item().map(_.name)) shouldBe Some("Entity 0")
        fx(row(t, 0).children().count(c => c.visible() && c.size.height() == 10.0)) shouldBe 0
        // far down: placeholders, and the page there asked for
        fx(t.list.scrollTo(1000000))
        fx(row(t, 1000000).item()) shouldBe None
        fx(server.asked.map(_.offset)) should contain(1000000)
        fx(server.answerAll())
        fx(row(t, 1000000).item().map(_.id)) shouldBe Some("e1000000")
        // a click on a heading the server sorts: the rows start again at the top, sorted there
        fx(t.header.cells(0).event.click @= PointerEvent(PointerKind.Click, 0, 0))
        fx(source.sort()) shouldBe Some(Sorting("name"))
        fx(t.list.offset()) shouldBe 0.0
        fx(server.asked.last) shouldBe PageRequest(0, 100, Some(Sorting("name")), None)
        fx(t.header.cells(0).event.click @= PointerEvent(PointerKind.Click, 0, 0))
        fx(server.answerAll())
        fx(row(t, 0).item().map(_.id)) shouldBe Some("e1899999")
        // a heading that does not sort does nothing
        fx(t.sortBy("id"))
        fx(source.sort()) shouldBe Some(Sorting("name", ascending = false))
      }
    }
    "choose rows by their tick boxes and the pointer, and every row honestly, the ones never loaded among them" in {
      val server = new Server(1900000)
      val (source, t) = table(server)
      onScreen(t) {
        fx(t.checks @= true)
        fx(t.startOf("name")) shouldBe VirtualTable.checkWidth
        click(t, 2, 10.0)
        click(t, 4, 10.0)
        fx(t.selection.keys()) shouldBe Set("e2", "e4")
        fx(row(t, 2).cells(0).asInstanceOf[CheckCell[Entity]].box.icon().name) shouldBe "check_box"
        click(t, 2, 10.0)
        fx(t.chosenCount) shouldBe 1
        // a click elsewhere on a row chooses it alone; Shift a range; Primary one more
        click(t, 6, 200.0)
        fx(t.selection.keys()) shouldBe Set("e6")
        click(t, 8, 200.0, none.copy(shift = true))
        fx(t.selection.keys()) shouldBe Set("e6", "e7", "e8")
        // a range far down: the rows it spans that are here and its ends, not a million keys
        fx(t.list.scrollTo(1000000))
        val started = System.nanoTime()
        click(t, 1000003, 200.0, none.copy(shift = true))
        info(f"a range over a million rows chosen in ${(System.nanoTime() - started) / 1e6}%.1f ms")
        fx(t.selection.keys().contains("e1000003")) shouldBe true
        fx(t.selection.keys().contains("e7")) shouldBe true
        fx(t.selection.keys().size) should be <= fx(source.kept) + 2
        click(t, 1000001, 200.0)
        fx(t.list.scrollTop(0.0))
        click(t, 6, 200.0)
        click(t, 8, 200.0, none.copy(shift = true))
        // the heading's box: every row, those never loaded among them; one taken out; then none
        fx(t.header.cells(0).event.click @= PointerEvent(PointerKind.Click, 0, 0))
        fx(t.selection.all()) shouldBe true
        fx(t.chosenCount) shouldBe 1900000
        fx(row(t, 3).selected()) shouldBe true
        click(t, 3, 10.0)
        fx(t.chosenCount) shouldBe 1899999
        fx(t.selection.keys()) shouldBe Set("e3")
        fx(t.selectedItems.size) shouldBe fx(source.kept) - 1
        fx(t.selectedItems.exists(_.id == "e3")) shouldBe false
        // a row far down, never loaded, is chosen too as it comes
        fx(t.list.scrollTo(1500000))
        fx(row(t, 1500000).selected()) shouldBe true
        // the heading's box with some left out chooses every row again; with every row chosen, none
        fx(t.header.cells(0).event.click @= PointerEvent(PointerKind.Click, 0, 0))
        fx(t.chosenCount) shouldBe 1900000
        fx(t.header.cells(0).event.click @= PointerEvent(PointerKind.Click, 0, 0))
        fx(t.chosenCount) shouldBe 0
        fx(t.selection.all()) shouldBe false
        // Primary+A is every row too, and a plain click one again
        press(KeyCode.A, "a", ctrl = true)
        fx(t.selection.all()) shouldBe true
        fx(t.list.scrollTop(0.0))
        click(t, 1, 200.0)
        fx(t.selection.all()) shouldBe false
        fx(t.selection.keys()) shouldBe Set("e1")
      }
    }
    "fill its width by the columns' weights, and move room between neighbours as an edge is dragged" in {
      val (_, t) = table(new Server(1000))
      onScreen(t) {
        fx(t.checks @= true)
        fx(t.fills @= true)
        // 564 shared 200:140:100 by the three columns after the tick boxes' 36
        fx(t.widthOf(VirtualTable.checkKey)) shouldBe VirtualTable.checkWidth
        fx(t.widthOf("name")) shouldBe 256.0
        fx(t.widthOf("status")) shouldBe 179.0
        fx(t.widthOf("id")) shouldBe 129.0
        fx(t.totalWidth) shouldBe 600.0
        fx(t.size.width @= 800.0)
        fx(t.totalWidth) shouldBe 800.0
        // narrower than the columns, they keep their widths and scroll sideways
        fx(t.size.width @= 300.0)
        fx(t.widthOf("name")) shouldBe 200.0
        fx(t.totalWidth) shouldBe 476.0
        fx(t.size.width @= 600.0)
        // dragged 40 wider, the name takes it from the status beside it; the id keeps its width
        fx(t.resize("name", t.widths(), 40.0))
        fx(t.widthOf("name")) shouldBe 296.0 +- 1.0
        fx(t.widthOf("status")) shouldBe 139.0 +- 1.0
        fx(t.widthOf("id")) shouldBe 129.0 +- 1.0
        fx(t.totalWidth) shouldBe 600.0
        // never below a column's least width
        fx(t.resize("name", t.widths(), 400.0))
        fx(t.widthOf("status")) shouldBe 40.0 +- 1.0
        fx(row(t, 0).cells(2).size.width()) shouldBe fx(t.widthOf("status"))
      }
    }
    "change its columns at runtime, rows and headings keeping the cells of the columns they still have" in {
      val (_, t) = table(new Server(1000))
      onScreen(t) {
        val name = fx(row(t, 0).cells(0))
        val heading = fx(t.header.cells(0))
        val status = fx(t.columns()(1))
        fx(t.columns @= Vector(t.columns()(0), GridColumn.served[Entity]("upper", "Upper", 120.0)(_.name.toUpperCase)))
        fx(row(t, 0).cells.size) shouldBe 2
        fx(row(t, 0).cells(0)) should be theSameInstanceAs name
        fx(t.header.cells(0)) should be theSameInstanceAs heading
        fx(row(t, 0).cells(1).asInstanceOf[TextCell[Entity]].label.content()) shouldBe "ENTITY 0"
        fx(t.header.cells.size) shouldBe 2
        fx(t.startOf("upper")) shouldBe 200.0
        fx(t.columns @= Vector(status, t.columns()(0)))
        fx(row(t, 0).cells(1)) should be theSameInstanceAs name
        fx(t.startOf("name")) shouldBe 140.0
      }
    }
    "wash the row under the pointer, and colour rows by the app's state under the selection's" in {
      val (_, t) = table(new Server(1000))
      onScreen(t) {
        val tint = Color.fromHex("33691e")
        fx(t.rowColor @= (e => if (e.status == "Inactive") Some(tint) else None))
        fx(row(t, 1).backgroundColor()) shouldBe tint
        fx(row(t, 2).backgroundColor()) shouldBe Color.Clear
        fx(t.selection.only("e1"))
        fx(row(t, 1).backgroundColor()) should not be tint
        val r = fx(row(t, 2))
        val wash = fx(r.children().head)
        fx(wash.visible()) shouldBe false
        fx(r.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0, 0))
        fx(wash.visible()) shouldBe true
        fx(wash.backgroundColor()) shouldBe fx(t.hoverColor())
        fx(r.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0, 0))
        fx(wash.visible()) shouldBe false
      }
    }
    "edit a cell through its menu from F2 or a click on it, without choosing its row" in {
      val (source, t) = table(new Server(1000))
      onScreen(t) {
        fx(t.current @= Some("e4"))
        press(KeyCode.F2, "F2")
        fx(Modal.showing) shouldBe true
        eventually(Focus.focused().exists(_.isInstanceOf[everyui.menu.MenuRow])) shouldBe true
        // Inactive (the row's own) is ticked; Active chosen: the source's row changes, the cell with it
        press(KeyCode.DOWN)
        press(KeyCode.ENTER, "Enter")
        eventually(!Modal.showing) shouldBe true
        fx(source.at(4).map(_.status)) shouldBe Some("Inactive")
        fx(t.current @= Some("e5"))
        press(KeyCode.F2, "F2")
        press(KeyCode.ENTER, "Enter")
        eventually(source.at(5).map(_.status).contains("Active")) shouldBe true
        fx(row(t, 5).cells(1).asInstanceOf[MenuCell[Entity]].label.content()) shouldBe "Active"
        // the pointer: a click on the cell's box opens it and leaves the choice as it was
        fx(t.selection.only("e1"))
        val cell = fx(row(t, 6).cells(1).asInstanceOf[MenuCell[Entity]])
        fx(cell.box.event.click @= PointerEvent(PointerKind.Click, cell.box.absoluteLeft + 4.0, cell.box.absoluteTop + 4.0))
        fx(Modal.showing) shouldBe true
        fx(t.selection.keys()) shouldBe Set("e1")
      }
    }
    "keep a footer of the app's under the rows" in {
      val (source, t) = table(new Server(1234))
      onScreen(t) {
        val line = fx(TextView.bound(source.total().map(n => n.toString + " results").getOrElse("Searching…")))
        fx(t.footer @= Some(line))
        fx(line.content()) shouldBe "1234 results"
        fx(line.position.top() + line.size.height()) shouldBe 400.0
        fx(t.list.position.top() + t.list.size.height()) shouldBe (400.0 - fx(line.size.height())) +- 0.5
        fx(t.footer @= None)
        fx(t.children().contains(line)) shouldBe false
        fx(t.list.size.height()) shouldBe 400.0 - TableHeader.height
      }
    }
    "scroll through 1.9 million rows quickly, keeping only its bound of pages" in {
      val server = new Server(1900000)
      val (source, t) = table(server, maxPages = 12)
      // a hundredth as long, scrolled beside it to the same rows (taken around its length), fetching as often
      val shortServer = new Server(19000)
      val (_, short) = table(shortServer, maxPages = 12)
      onScreen(t) {
        fx(ui.children += short)
        val made = fx(t.list.rows.size)
        // a wheel's worth at a time near the top, then jumps through the whole length, as a thumb dragged does
        def wheel(table: VirtualTable[Entity])(i: Int): Unit = table.list.scrollBy(40.0)
        def jump(table: VirtualTable[Entity], rows: Int)(i: Int): Unit = table.list.scrollTop((i * 950 % rows) * 28.0)
        // the work counted, which no load changes: each step fits and reads the words of the rows it shows, a few
        // times each at most, and fetches a page or two, nothing that grows with the length or the pages kept
        val cells = made * 3
        val counts = List(counted(2000, "fits", "measured")(wheel(t)),
          counted(2000, "fits", "measured")(jump(t, 1900000)))
        val fetched = server.asked.size
        counts.zip(List("a wheel's", "a jump's")).foreach { case (c, step) =>
          info(s"$step fits ${c("fits")._1 / 2000.0} a step (most ${c("fits")._2}), sizes read " +
            s"${c("measured")._1 / 2000.0} (most ${c("measured")._2}), for $cells cells made")
          c("fits")._2 should be <= cells * 3
          c("measured")._2 should be <= cells * 3
        }
        info(s"$fetched pages fetched in 4000 steps")
        fetched should be <= 4000 * 3
        // and timed in the FX thread's own time beside the short table, step for step: a step that grew with the
        // length (by a hundred) would cost many times what the short one's does
        fx(List(t, short).foreach(_.list.scrollTop(0.0)))
        val (wheelLong, wheelShort) = workedBeside(2000)(wheel(t), wheel(short))
        val (jumpLong, jumpShort) = workedBeside(2000)(jump(t, 1900000), jump(short, 19000))
        val (long, shorter) = (wheelLong + jumpLong, wheelShort + jumpShort)
        info(f"4000 scroll steps over 1.9M rows: $long%.0f ms worked (${long / 4000.0}%.3f ms a step); over 19k " +
          f"beside them: $shorter%.0f ms (${long / shorter}%.2f times)")
        long should be < shorter * 3.0
        // and 10 ms a step at most: some 0.4 ms on a quiet machine, and 2.8 the slowest seen, in a whole run of the
        // specs at a load of 26 to 40
        long should be < 40000.0
        fx(t.list.rows.size) should be <= made + 5
        fx(source.kept) should be <= 12 * 100
        info(s"${server.asked.size} pages fetched, ${fx(source.kept)} rows kept")
      }
    }
  }
}
