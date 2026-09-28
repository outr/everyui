package everyui.table

import everyui.component.{Component, Container}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.kit.Tag
import everyui.layout.HorizontalLayout
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** The virtual table: cells made once per row and reused, headings that sort and columns a pointer widens, a sideways
  * scroll, rows chosen by the keys, a mirrored layout, and pills in a cell. */
class TableSpec extends FxSpec {
  case class Commit(hash: String, subject: String, author: String, time: Long, refs: List[String] = Nil)

  /** The subject with its refs as pills after it. */
  class SubjectCell extends GridCell[Commit] {
    val refs: Container = new Container {
      layout @= Some(HorizontalLayout(4.0, middle = true))
      children := item().toVector.flatMap(_.refs).map(r => new Tag(r, Tag.tone("success"), soft = true))
      position.top @= 6.0
    }
    val subject: everyui.component.TextView = everyui.component.TextView.bound(item().map(_.subject).getOrElse(""), 13.0)
    subject.position.left := refs.size.width() + 8.0
    children ++= List(refs, subject)
  }

  private def commits(n: Int): Var[Vector[Commit]] = Var((0 until n).map { i =>
    Commit("h" + i, "Change " + i, if (i % 3 == 0) "Ann" else "Bob", 1000L - i, if (i == 0) List("main", "v1") else Nil)
  }.toVector)

  private def table(items: Var[Vector[Commit]]): VirtualTable[Commit] = new VirtualTable[Commit](items, _.hash, _.subject,
    Vector(
      GridColumn[Commit]("subject", "Subject", 300.0, () => new SubjectCell, sort = Some(Ordering.by(_.subject))),
      GridColumn.text[Commit]("author", "Author", 120.0, sorts = true)(_.author),
      GridColumn.text[Commit]("date", "Date", 120.0, sortBy = Some(Ordering.by(_.time)))(_.time.toString)))

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double): Unit = fx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, true, false, false, false, false, true, null))
  }

  private def press(code: KeyCode, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, shift, false, false, false))
  }

  private def onScreen[T](t: VirtualTable[?])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += t
      t.focus()
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def texts(t: VirtualTable[Commit], column: Int): List[String] =
    fx((0 until 4).flatMap(i => t.list.rowAt(i)).map(r => r.asInstanceOf[TableRow[Commit]].cells(column))
      .collect { case c: TextCell[Commit] @unchecked => c.label.content() }.toList)

  "A virtual table" should {
    "make a row's cells once, and hand them the items as it scrolls" in {
      val items = commits(50000)
      val t = fx(table(items))
      onScreen(t) {
        val made = fx(t.list.rows.size)
        made should be < 30
        val row = fx(t.list.rowAt(0).get.asInstanceOf[TableRow[Commit]])
        fx(row.cells.map(_.item().map(_.hash))) shouldBe Vector(Some("h0"), Some("h0"), Some("h0"))
        fx(row.cells(0).asInstanceOf[SubjectCell].refs.children().collect { case p: Tag => p.kind }) shouldBe
          Vector("Tag(main)", "Tag(v1)")
        val started = System.nanoTime()
        fx((0 until 20000 by 5).foreach(y => t.list.scrollTop(y.toDouble)))
        info(f"4000 scroll steps over 50k rows of 3 columns: ${(System.nanoTime() - started) / 1e6}%.0f ms")
        fx(t.list.rows.size) should be <= made + 5
        fx(t.list.rows.flatMap(_.asInstanceOf[TableRow[Commit]].cells).size) shouldBe fx(t.list.rows.size) * 3
      }
    }
    "sort by a heading clicked, up, down and back" in {
      val t = fx(table(commits(6)))
      onScreen(t) {
        texts(t, 1) shouldBe List("Ann", "Bob", "Bob", "Ann")
        val author = fx(t.header.cells(1))
        fx(author.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0, 0))
        fx(t.sort()) shouldBe Some(Sorting("author"))
        texts(t, 1) shouldBe List("Ann", "Ann", "Bob", "Bob")
        fx(t.sortBy("author"))
        texts(t, 1) shouldBe List("Bob", "Bob", "Bob", "Bob")
        fx(t.sortBy("author"))
        fx(t.sort()) shouldBe None
        fx(t.sortBy("date"))
        texts(t, 2) shouldBe List("995", "996", "997", "998")
        // the app sorting it instead
        fx(t.sortsItself @= false)
        texts(t, 2) shouldBe List("1000", "999", "998", "997")
      }
    }
    "widen a column dragged by its heading's edge, and scroll sideways when they overflow" in {
      val t = fx(table(commits(20)))
      onScreen(t) {
        fx(t.size.width @= 500.0)
        val header = fx(t.header.cells(0))
        val handle = fx(header.children().last)
        val x = fx(handle.absoluteLeft + 2.0)
        mouse(handle, MouseEvent.MOUSE_PRESSED, x, 10.0)
        mouse(handle, MouseEvent.MOUSE_DRAGGED, x + 100.0, 10.0)
        mouse(handle, MouseEvent.MOUSE_RELEASED, x + 100.0, 10.0)
        fx(t.widths()) shouldBe Map("subject" -> 400.0)
        fx(t.startOf("author")) shouldBe 400.0
        fx(t.list.rowAt(0).get.asInstanceOf[TableRow[Commit]].cells(1).position.left()) shouldBe 400.0
        fx(t.totalWidth) shouldBe 640.0
        // wider than the table: Right scrolls the columns, the rows follow
        press(KeyCode.RIGHT)
        fx(t.scrollX()) shouldBe VirtualTable.step
        fx(t.list.rowAt(0).get.asInstanceOf[TableRow[Commit]].cells(1).position.left()) shouldBe 400.0 - VirtualTable.step
        fx(t.header.cells(1).position.left()) shouldBe 400.0 - VirtualTable.step
        // a column hidden takes no room
        fx(t.hidden @= Set("subject"))
        fx(t.startOf("author")) shouldBe 0.0
        fx(t.scrollX()) shouldBe 0.0
      }
    }
    "choose rows with the keys, several with Shift" in {
      val t = fx(table(commits(20)))
      onScreen(t) {
        press(KeyCode.DOWN)
        press(KeyCode.DOWN, shift = true)
        fx(t.selectedItems.map(_.hash)) shouldBe Vector("h1", "h2")
        fx(t.selection.multiple @= false)
        press(KeyCode.DOWN, shift = true)
        fx(t.selection.keys()) shouldBe Set("h3")
      }
    }
    "mirror its columns right to left" in {
      val t = fx(table(commits(5)))
      onScreen(t) {
        fx(Direction.set(true))
        fx(t.size.width @= 700.0)
        fx(t.header.cells(0).position.left()) shouldBe 400.0
        fx(t.list.rowAt(0).get.asInstanceOf[TableRow[Commit]].cells(1).position.left()) shouldBe 280.0
      }
    }
  }

  "A tag" should {
    "fit its word, read on its colour, and take no room when empty" in {
      val word = Var("main")
      val tag = fx(new Tag(word(), Theme.accent()))
      fx {
        ui.children.clear()
        ui.children += tag
      }
      fx(tag.size.width()) should be > 20.0
      Tag.ink(everyui.Color.White) shouldBe everyui.Color.fromHex("1c1b1f")
      Tag.ink(everyui.Color.Black) shouldBe everyui.Color.White
      fx(word @= "")
      fx(tag.size.width()) shouldBe 0.0
      fx(ui.children.clear())
    }
  }
}
