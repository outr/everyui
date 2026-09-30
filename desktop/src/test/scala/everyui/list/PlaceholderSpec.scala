package everyui.list

import everyui.async.Task
import everyui.component.{Component, Container, TextView}
import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.paging.{Page, PageRequest, PagedSource}
import everyui.table.{GridColumn, TableHeader, VirtualTable}
import everyui.{FxSpec, ui}
import javafx.scene.input.KeyCode
import reactify.*

/** A list over a server's rows stands in for the rows not come (a placeholder, never the app's row with no item),
  * shows its loading view while a query's first page is awaited and its empty view when it finds nothing, swaps rows
  * and placeholders both ways as pages come and the rows scroll, and keeps the cursor usable across them. */
class PlaceholderSpec extends FxSpec {
  /** A server of `total` rows, holding each answer until the spec gives it. */
  class Server(var total: Int) {
    var owed: Vector[() => Unit] = Vector.empty

    def fetch(request: PageRequest): Task[Either[String, Page[String]]] = Task.async { done =>
      val rows = (request.offset until math.min(total, request.offset + request.limit)).map(i => "row " + i).toVector
      val count = total
      owed = owed :+ (() => done(Right(Page(rows, Some(count)))))
    }

    def answerAll(): Unit = while (owed.nonEmpty) {
      val next = owed.head
      owed = owed.tail
      next()
    }
  }

  /** A row of the app's that draws chrome (a button) whatever its item. */
  class AppRow extends ListRow[String] {
    val button: Container = new Container {
      size.width @= 20.0
      size.height @= 20.0
    }
    children += button
  }

  private def list(server: Server): (PagedSource[String], VirtualList[String]) = fx {
    val source = new PagedSource[String](s => s, pageSize = 100)(server.fetch)
    val l = new VirtualList[String](Var(Vector.empty), s => s, s => s, source = Some(source))(() => new AppRow)
    l.size.height @= 280.0
    (source, l)
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
      c.focus()
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def press(code: KeyCode, name: String): Unit = fx {
    javafx.event.Event.fireEvent(window, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, name,
      name, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED, name,
      name, code, false, false, false, false))
  }

  // the rows that show, and whether any is an app's row with no item
  private def showing(l: VirtualList[String]): Vector[ListRow[String]] = l.rows.filter(_.visible())
  private def emptyAppRows(l: VirtualList[String]): Int = showing(l).count(r => !r.standsIn && r.item().isEmpty)

  "A list over a paged source" should {
    "stand in for its rows while the first page is awaited, and show them when it comes" in {
      val server = new Server(1000)
      val (_, l) = list(server)
      onScreen(l) {
        fx(l.count) shouldBe 100
        fx(showing(l).nonEmpty) shouldBe true
        fx(showing(l).forall(_.standsIn)) shouldBe true
        fx(l.rowAt(0).map(_.present())) shouldBe Some(false)
        fx(server.answerAll())
        fx(l.count) shouldBe 1000
        fx(l.rowAt(0).map(_.standsIn)) shouldBe Some(false)
        fx(l.rowAt(0).flatMap(_.item())) shouldBe Some("row 0")
        fx(l.rowAt(0).map(_.present())) shouldBe Some(true)
        fx(emptyAppRows(l)) shouldBe 0
      }
    }
    "stand in for rows beyond the pages loaded until they come, reusing rows and placeholders both ways" in {
      val server = new Server(100000)
      val (_, l) = list(server)
      onScreen(l) {
        fx(server.answerAll())
        val made = fx(l.rows.size)
        // far down: placeholders, never the app's rows with no item
        fx(l.scrollTo(50000))
        fx(l.rowAt(50000).map(_.standsIn)) shouldBe Some(true)
        fx(emptyAppRows(l)) shouldBe 0
        fx(server.answerAll())
        fx(l.rowAt(50000).flatMap(_.item())) shouldBe Some("row 50000")
        fx(showing(l).forall(!_.standsIn)) shouldBe true
        // back and forth: the app's rows and the placeholders made once, then handed on
        val settled = fx(l.rows.size)
        settled should be <= made * 2
        fx(l.scrollTo(80000))
        fx(emptyAppRows(l)) shouldBe 0
        fx(l.scrollTo(50000))
        fx(showing(l).forall(r => !r.standsIn && r.item().nonEmpty)) shouldBe true
        fx(l.scrollTo(80000))
        fx(showing(l).forall(_.standsIn)) shouldBe true
        fx(l.rows.size) shouldBe settled
        fx(server.answerAll())
        fx(showing(l).forall(r => !r.standsIn && r.item().nonEmpty)) shouldBe true
        fx(l.rows.size) shouldBe settled
      }
    }
    "stand in again after an invalidate, or show its loading view, never stale rows with no item" in {
      val server = new Server(1000)
      val (source, l) = list(server)
      onScreen(l) {
        fx(server.answerAll())
        fx(source.invalidate())
        fx(showing(l).nonEmpty) shouldBe true
        fx(showing(l).forall(_.standsIn)) shouldBe true
        fx(server.answerAll())
        fx(showing(l).forall(r => !r.standsIn && r.item().nonEmpty)) shouldBe true
        // with a loading view, it shows in place of the rows until the first page comes
        val loading = fx(VirtualList.loading())
        fx(l.loadingView @= Some(loading))
        fx(loading.visible()) shouldBe false
        fx(source.invalidate())
        fx(loading.visible()) shouldBe true
        fx(showing(l)) shouldBe empty
        fx(server.answerAll())
        fx(loading.visible()) shouldBe false
        fx(l.rowAt(0).flatMap(_.item())) shouldBe Some("row 0")
      }
    }
    "show its loading view while the first page is awaited, in the middle of its box" in {
      val server = new Server(1000)
      val (_, l) = list(server)
      val loading = fx(VirtualList.loading())
      fx(l.loadingView @= Some(loading))
      onScreen(l) {
        fx(loading.visible()) shouldBe true
        fx(loading.parent().exists(_ eq l)) shouldBe true
        fx(loading.position.top()) shouldBe ((280.0 - loading.size.height()) / 2.0)
        fx(showing(l)) shouldBe empty
        fx(server.owed.size) should be > 0
        fx(server.answerAll())
        fx(loading.visible()) shouldBe false
        fx(showing(l).nonEmpty) shouldBe true
      }
    }
    "show its empty view when a query finds nothing" in {
      val server = new Server(0)
      val (source, l) = list(server)
      val nothing = fx(VirtualList.empty())
      fx(l.emptyView @= Some(nothing))
      onScreen(l) {
        // not while it is still looking
        fx(nothing.visible()) shouldBe false
        fx(server.answerAll())
        fx(source.total()) shouldBe Some(0)
        fx(l.count) shouldBe 0
        fx(nothing.visible()) shouldBe true
        fx(nothing.asInstanceOf[TextView].content()) shouldBe "No results"
        fx(showing(l)) shouldBe empty
        server.total = 5
        fx(source.invalidate())
        fx(server.answerAll())
        fx(nothing.visible()) shouldBe false
        fx(showing(l).size) shouldBe 5
      }
    }
    "keep the cursor on a placeholder usable, and move it onto the row when it comes" in {
      val server = new Server(1000)
      val (_, l) = list(server)
      var activated = Vector.empty[String]
      fx(l.activated.attach(s => activated = activated :+ s))
      onScreen(l) {
        fx(Focus.focused().exists(_ eq l)) shouldBe true
        // the cursor on the first place, drawn on the placeholder standing there
        fx(l.current()) shouldBe Some(PagedSource.placeKey(0))
        fx(l.rowAt(0).map(r => r.standsIn && r.current())) shouldBe Some(true)
        press(KeyCode.DOWN, "")
        press(KeyCode.DOWN, "")
        fx(l.current()) shouldBe Some(PagedSource.placeKey(2))
        fx(l.rowAt(2).map(r => r.standsIn && r.current())) shouldBe Some(true)
        // Enter on a row not come does nothing
        press(KeyCode.ENTER, "")
        activated shouldBe empty
        fx(server.answerAll())
        fx(l.current()) shouldBe Some("row 2")
        fx(l.rowAt(2).map(r => !r.standsIn && r.current())) shouldBe Some(true)
        press(KeyCode.ENTER, "")
        activated shouldBe Vector("row 2")
        fx(Focus.focused().exists(_ eq l)) shouldBe true
      }
    }
    "keep the focus while its loading view shows, letting the arrows go on" in {
      val server = new Server(1000)
      val (source, l) = list(server)
      fx(l.loadingView @= Some(VirtualList.loading()))
      onScreen(l) {
        fx(Focus.focused().exists(_ eq l)) shouldBe true
        fx(l.keepsEvent(KeyEvent(Key.ArrowDown, "ArrowDown"))) shouldBe false
        fx(server.answerAll())
        fx(l.keepsEvent(KeyEvent(Key.ArrowDown, "ArrowDown"))) shouldBe true
        fx(source.invalidate())
        fx(l.keepsEvent(KeyEvent(Key.ArrowDown, "ArrowDown"))) shouldBe false
        fx(Focus.focused().exists(_ eq l)) shouldBe true
      }
    }
    "hand the app's rows no item where it has no placeholder, and take another placeholder at once" in {
      val server = new Server(1000)
      val (_, l) = list(server)
      onScreen(l) {
        fx(showing(l).forall(_.standsIn)) shouldBe true
        fx(l.placeholder @= None)
        fx(showing(l).nonEmpty) shouldBe true
        fx(showing(l).forall(r => !r.standsIn && !r.present())) shouldBe true
        fx(l.rows.count(_.standsIn)) shouldBe 0
        var made = 0
        fx(l.placeholder @= Some(() => {
          made += 1
          new Container
        }))
        fx(showing(l).forall(_.standsIn)) shouldBe true
        made shouldBe fx(showing(l).size)
      }
    }
  }

  "A paged table" should {
    "show its loading and empty views under its headings, and take a placeholder of the app's" in {
      val server = new Server(0)
      val (source, t) = fx {
        val source = new PagedSource[String](s => s, pageSize = 100)(server.fetch)
        val t = VirtualTable.paged[String](source, s => s, Vector(GridColumn.served[String]("name", "Name", 200.0)(s => s)))
        t.size.width @= 600.0
        t.size.height @= 400.0
        (source, t)
      }
      val loading = fx(VirtualList.loading())
      val nothing = fx(VirtualList.empty())
      fx {
        t.loadingView @= Some(loading)
        t.emptyView @= Some(nothing)
      }
      onScreen(t) {
        fx(loading.visible()) shouldBe true
        fx(loading.absoluteTop - t.absoluteTop) should be > TableHeader.height
        fx(server.answerAll())
        fx(loading.visible()) shouldBe false
        fx(nothing.visible()) shouldBe true
        fx(nothing.absoluteTop - t.absoluteTop) should be > TableHeader.height
        // its rows draw their own grey bars, unless the app gives a placeholder
        server.total = 1000
        fx(t.loadingView @= None)
        fx(source.invalidate())
        fx(t.list.rowAt(0).map(_.standsIn)) shouldBe Some(false)
        fx(t.placeholder @= Some(() => VirtualList.blank()))
        fx(t.list.rowAt(0).map(_.standsIn)) shouldBe Some(true)
        fx(server.answerAll())
        fx(t.list.rowAt(0).map(_.standsIn)) shouldBe Some(false)
        fx(t.list.rowAt(0).flatMap(_.item())) shouldBe Some("row 0")
      }
    }
  }
}
