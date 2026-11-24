package everyui.table

import everyui.component.{Component, Container, Modal, ScrollContainer}
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent, Pointer, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.menu.{Menu, MenuItem}
import everyui.theme.Theme
import everyui.{FxSpec, ui}

/** A `DataTable`'s edits pending and its rows' menus on the desktop's nodes: a changed cell, a new row and a deleted
  * one drawn in their tints and told to a screen reader; Revert in a cell's and a row's menu calling the app back; a
  * row's menu opened by the secondary button, a finger held, the menu key, Shift+F10 and the ⋮, its entries acting on
  * the row it was opened on or the rows chosen with it, the choice, the keys and the scroll working as before. */
class DataTableEditSpec extends FxSpec {
  private val columns = Vector(DataColumn("Name"), DataColumn("Age"), DataColumn("City"))
  private val people = Vector(Vector("Grace", "85", "Arlington"), Vector("Alan", "41", "Wilmslow"),
    Vector("Ada", "36", "London"), Vector("Edsger", "72", "Nuenen"))

  private def onScreen[T](view: Component, width: Double = 700.0)(test: => T): T = {
    fx {
      ui.children.clear()
      view.size.width @= width
      ui.children += view
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def scroll(t: DataTable): ScrollContainer = t.children().collectFirst { case s: ScrollContainer => s }.get
  private def grid(t: DataTable): Container = scroll(t).content.children().head.asInstanceOf[Container]
  private def rowsScroll(t: DataTable): ScrollContainer =
    grid(t).children().collectFirst { case s: ScrollContainer => s }.get
  private def body(t: DataTable): Container = rowsScroll(t).content.children().head.asInstanceOf[Container]
  private def row(t: DataTable, name: String): DataRow =
    body(t).children().collectFirst { case r: DataRow if r.content.head.text == name => r }.get
  private def drawn(t: DataTable): Vector[String] =
    body(t).children().collect { case r: DataRow => r.content.head.text }
  // the washes and bars a row draws for its edits: its children that are neither its cells nor its rules
  private def marks(r: DataRow): Vector[Container] = r.children().collect {
    case c: Container if !r.cells.exists(_ eq c) && c.size.height() > 1.0 => c
  }
  private def menu(): Option[Menu] = Modal.top.collect { case m: Menu => m }
  private def labels(): List[String] = fx(menu().map(_.rows.map(_.item.label)).getOrElse(Nil))
  private def choose(label: String): Unit = fx(menu().get.rows.find(_.item.label == label).get.chosen @= (()))
  // the secondary button over a cell, as the platform hears it; whether the app answered it
  private def rightClick(t: DataTable, cell: Component): Boolean = fx {
    Pointer.consumed = None
    grid(t).event.pointer.heard(grid(t).event.pointer.menu, PointerKind.Menu, cell.absoluteLeft + 2.0,
      cell.absoluteTop + 2.0)
    Pointer.menuAnswered
  }
  private def hold(t: DataTable, cell: Component): Unit = fx(grid(t).event.pointer.menu @=
    PointerEvent(PointerKind.Menu, cell.absoluteLeft + 2.0, cell.absoluteTop + 2.0, finger = true))
  private def click(t: DataTable, cell: Component, ctrl: Boolean = false): Unit = fx(grid(t).event.click @=
    PointerEvent(PointerKind.Click, cell.absoluteLeft + 2.0, cell.absoluteTop + 2.0, ctrl = ctrl))
  private def key(t: DataTable, k: Key, name: String, shift: Boolean = false): Unit =
    fx(grid(t).event.key.down @= KeyEvent(k, name, shift = shift))
  private def table(): DataTable = fx(DataTable(columns, people))

  "A data table's edits pending" should {
    "be drawn in their tints, a deleted row dimmed and struck through, and drawn as before once cleared" in {
      val t = table()
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Ada").size.height() > 0.0) shouldBe true
        fx {
          t.cellStates @= Map((0, 1) -> EditState.Changed)
          t.rowStates @= Map(2 -> EditState.New, 3 -> EditState.Deleted, 0 -> EditState.Changed)
        }
        val grace = row(t, "Grace")
        // the changed cell's wash over its column, as tall as the row, and the row's bar in the warning colour
        fx(marks(grace).map(_.backgroundColor())).toSet shouldBe
          fx(Set(DataRow.cellTint(EditState.Changed), DataRow.colour(EditState.Changed)))
        val wash = fx(marks(grace).find(_.backgroundColor() == DataRow.cellTint(EditState.Changed)).get)
        fx(wash.position.left()) shouldBe fx(t.columnLeft(1))
        fx(wash.size.width()) shouldBe fx(t.columnWidth(1))
        fx(wash.size.height()) shouldBe fx(grace.size.height())
        // a changed row keeps its stripe, a new one is washed in the success colour, a deleted one in the danger colour
        fx(row(t, "Ada").backgroundColor()) shouldBe fx(DataRow.rowTint(EditState.New).get)
        val edsger = row(t, "Edsger")
        fx(edsger.backgroundColor()) shouldBe fx(DataRow.rowTint(EditState.Deleted).get)
        fx(edsger.opacity()) shouldBe DataRow.deletedOpacity
        fx(edsger.cells.flatMap(_.runs()).map(_.strike).distinct) shouldBe Vector(true)
        fx(marks(edsger).map(_.backgroundColor())) shouldBe Vector(fx(DataRow.colour(EditState.Deleted)))
        // the tints are the theme's own, faint enough to read through
        fx(DataRow.colour(EditState.New)) shouldBe fx(Theme.success())
        DataRow.cellTint(EditState.Changed).alpha should be < 0.3
        // a row with nothing pending is as it was
        fx(marks(row(t, "Alan"))) shouldBe empty
        fx(row(t, "Alan").cells.flatMap(_.runs()).map(_.strike).distinct) shouldBe Vector(false)
        fx {
          t.cellStates @= Map.empty
          t.rowStates @= Map.empty
        }
        fx(marks(grace)) shouldBe empty
        fx(marks(edsger)) shouldBe empty
        fx(edsger.opacity()) shouldBe 1.0
        fx(edsger.cells.flatMap(_.runs()).map(_.strike).distinct) shouldBe Vector(false)
        fx(edsger.backgroundColor()) shouldNot be(fx(DataRow.rowTint(EditState.Deleted).get))
      }
    }
    "be told to a screen reader: a cell changed, a row new, deleted or changed" in {
      val t = table()
      onScreen(t) {
        eventually(drawn(t).size == 4) shouldBe true
        fx {
          t.cellStates @= Map((1, 2) -> EditState.Changed)
          t.rowStates @= Map(2 -> EditState.New, 3 -> EditState.Deleted, 1 -> EditState.Changed)
        }
        fx(row(t, "Alan").cells(2).semantics()) shouldBe Semantics("cell", description = "changed")
        fx(row(t, "Alan").cells(1).semantics()) shouldBe Semantics("cell")
        fx(row(t, "Alan").semantics().description) shouldBe "changed"
        fx(row(t, "Ada").semantics().description) shouldBe "new"
        fx(row(t, "Edsger").semantics().description) shouldBe "deleted"
        fx(row(t, "Grace").semantics().description) shouldBe ""
        // and after what a row that opens is told
        fx(t.onOpen @= Some((_: Int) => ()))
        fx(row(t, "Edsger").semantics().description) shouldBe "deleted, Enter to open"
        // in the language in use
        fx(everyui.i18n.Words.languages.values.forall(w => Vector("table.changed", "table.new", "table.deleted",
          "table.revertCell", "table.revertRow", "table.revertRows", "table.rowActions").forall(w.contains))) shouldBe
          true
      }
    }
    "be drawn on rows made later, as a long table scrolls" in {
      val many = (0 until 200).toVector.map(i => Vector("row " + i, i.toString, "x"))
      val t = fx(DataTable(columns, many))
      fx(t.maxHeight @= 240.0)
      fx(t.rowStates @= Map(150 -> EditState.Deleted))
      onScreen(t) {
        eventually(t.windowed() && drawn(t).nonEmpty) shouldBe true
        drawn(t) shouldNot contain("row 150")
        fx(t.openMenu(150, Some(0))) shouldBe false
        eventually(drawn(t).contains("row 150")) shouldBe true
        fx(row(t, "row 150").opacity()) shouldBe DataRow.deletedOpacity
        fx(row(t, "row 150").semantics().description) shouldBe "deleted"
      }
    }
  }

  "A data table's Revert" should {
    "be offered in a cell's and a row's menu where the app reverts, and hand it what to put back" in {
      val t = table()
      val asked = new java.util.concurrent.ConcurrentLinkedQueue[Revert]()
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Grace").size.height() > 0.0) shouldBe true
        fx {
          t.cellStates @= Map((0, 1) -> EditState.Changed)
          t.rowStates @= Map(3 -> EditState.Deleted)
        }
        // nothing to offer while the app reverts nothing: the page's own menu stays
        rightClick(t, row(t, "Grace").cells(1)) shouldBe false
        fx(Modal.showing) shouldBe false
        fx(t.onRevert @= Some((r: Revert) => asked.add(r)))
        // a changed cell: Revert cell and Revert row
        rightClick(t, row(t, "Grace").cells(1)) shouldBe true
        labels() shouldBe List("Revert cell", "Revert row")
        choose("Revert cell")
        asked.poll() shouldBe Revert(0, Some(1))
        fx(Modal.showing) shouldBe false
        // another cell of that row: the row only
        rightClick(t, row(t, "Grace").cells(2)) shouldBe true
        labels() shouldBe List("Revert row")
        choose("Revert row")
        asked.poll() shouldBe Revert(0, None)
        // a deleted row
        rightClick(t, row(t, "Edsger").cells(0)) shouldBe true
        labels() shouldBe List("Revert row")
        choose("Revert row")
        asked.poll() shouldBe Revert(3, None)
        // a row with nothing pending offers nothing
        rightClick(t, row(t, "Alan").cells(0)) shouldBe false
        fx(Modal.showing) shouldBe false
        asked.isEmpty shouldBe true
      }
    }
    "revert each of the rows chosen with the one it was opened on that has an edit" in {
      val t = table()
      val asked = new java.util.concurrent.ConcurrentLinkedQueue[Revert]()
      fx(t.onRevert @= Some((r: Revert) => asked.add(r)))
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Grace").size.height() > 0.0) shouldBe true
        fx {
          t.rowStates @= Map(1 -> EditState.New, 3 -> EditState.Deleted)
          t.selection @= Set(0, 1, 3)
        }
        rightClick(t, row(t, "Edsger").cells(0)) shouldBe true
        labels() shouldBe List("Revert 2 rows")
        choose("Revert 2 rows")
        Vector(asked.poll(), asked.poll()) shouldBe Vector(Revert(1), Revert(3))
        fx(t.selection()) shouldBe Set(0, 1, 3)
      }
    }
  }

  "A data table's row menu" should {
    "open by the secondary button, a finger held, the menu key and Shift+F10, on the row asked for" in {
      val t = table()
      val heard = new java.util.concurrent.ConcurrentLinkedQueue[String]()
      fx(t.rowMenu @= Some((ref: RowRef) => List(MenuItem("Delete row", () => heard.add("delete " + ref)))))
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Grace").size.height() > 0.0) shouldBe true
        rightClick(t, row(t, "Ada").cells(2)) shouldBe true
        labels() shouldBe List("Delete row")
        choose("Delete row")
        heard.poll() shouldBe "delete " + RowRef(2, Vector(2), Some(2))
        // a finger held
        hold(t, row(t, "Alan").cells(0))
        labels() shouldBe List("Delete row")
        choose("Delete row")
        heard.poll() shouldBe "delete " + RowRef(1, Vector(1), Some(0))
        // the keys: the menu key on the cell they are on (the hold left them on Alan's), then Shift+F10
        fx(grid(t).focus())
        key(t, Key.ArrowDown, "ArrowDown")
        key(t, Key.ArrowRight, "ArrowRight")
        fx(grid(t).keepsEvent(KeyEvent(Key.ContextMenu, "ContextMenu"))) shouldBe true
        key(t, Key.ContextMenu, "ContextMenu")
        labels() shouldBe List("Delete row")
        // under the cell the keys are on
        val m = fx(menu().get)
        fx(m.position.top()) shouldBe fx(row(t, "Ada").absoluteTop + row(t, "Ada").size.height()) +- 1.0
        choose("Delete row")
        heard.poll() shouldBe "delete " + RowRef(2, Vector(2), Some(1))
        fx(grid(t).focus())
        key(t, Key.ArrowDown, "ArrowDown")
        key(t, Key.Other, "F10", shift = true)
        labels() shouldBe List("Delete row")
        choose("Delete row")
        heard.poll() shouldBe "delete " + RowRef(3, Vector(3), Some(1))
        // F10 alone is not the menu's
        fx(grid(t).keepsEvent(KeyEvent(Key.Other, "F10"))) shouldBe false
      }
    }
    "act on the rows chosen where the row is one of them, else on it alone, choosing nothing" in {
      val t = table()
      var refs = Vector.empty[RowRef]
      fx(t.rowMenu @= Some((ref: RowRef) => List(MenuItem("Delete row", () => refs = refs :+ ref))))
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Grace").size.height() > 0.0) shouldBe true
        fx(t.sort @= Some(ColumnSort(0)))
        click(t, row(t, "Grace").cells(0))
        click(t, row(t, "Alan").cells(0), ctrl = true)
        fx(t.selection()) shouldBe Set(0, 1)
        // on one of the rows chosen: those rows, in the order they show (Alan before Grace)
        rightClick(t, row(t, "Grace").cells(1)) shouldBe true
        choose("Delete row")
        fx(refs.last) shouldBe RowRef(0, Vector(1, 0), Some(1))
        // on one not chosen: it alone, and the choice as it was
        rightClick(t, row(t, "Edsger").cells(0)) shouldBe true
        choose("Delete row")
        fx(refs.last) shouldBe RowRef(3, Vector(3), Some(0))
        fx(t.selection()) shouldBe Set(0, 1)
        // a menu closed with nothing chosen does nothing either
        rightClick(t, row(t, "Ada").cells(0)) shouldBe true
        fx(Modal.close())
        refs.size shouldBe 2
        fx(t.selection()) shouldBe Set(0, 1)
      }
    }
    "merge Revert in after the app's entries, and open nothing for a row with none" in {
      val t = table()
      fx {
        t.rowMenu @= Some((ref: RowRef) => if (ref.row == 0) Nil else List(MenuItem("Delete row")))
        t.onRevert @= Some((_: Revert) => ())
        t.rowStates @= Map(1 -> EditState.New, 0 -> EditState.Changed)
      }
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Grace").size.height() > 0.0) shouldBe true
        rightClick(t, row(t, "Alan").cells(0)) shouldBe true
        labels() shouldBe List("Delete row", "", "Revert row")
        fx(Modal.close())
        // the app's none, but a revert
        rightClick(t, row(t, "Grace").cells(0)) shouldBe true
        labels() shouldBe List("Revert row")
        fx(Modal.close())
        fx(t.rowStates @= Map.empty)
        rightClick(t, row(t, "Grace").cells(0)) shouldBe false
        fx(Modal.showing) shouldBe false
      }
    }
    "leave the choice, the keys and the scroll as they were" in {
      val many = (0 until 120).toVector.map(i => Vector("row " + i, i.toString, "x"))
      val t = fx(DataTable(columns, many))
      fx(t.maxHeight @= 240.0)
      fx(t.rowMenu @= Some((_: RowRef) => List(MenuItem("Delete row"))))
      onScreen(t) {
        eventually(t.windowed() && drawn(t).contains("row 2")) shouldBe true
        // a click chooses, again lets go, as ever
        click(t, row(t, "row 2").cells(0))
        fx(t.selection()) shouldBe Set(2)
        click(t, row(t, "row 2").cells(0))
        fx(t.selection()) shouldBe Set()
        // the keys move from the cell clicked, and choose
        fx(grid(t).focus())
        key(t, Key.ArrowDown, "ArrowDown")
        key(t, Key.Space, " ")
        fx(t.selection()) shouldBe Set(3)
        // the rows scroll, and those that come are drawn
        fx(rowsScroll(t).scrollTo(2000.0))
        eventually(drawn(t).exists(_.drop(4).toInt > 40)) shouldBe true
        drawn(t) shouldNot contain("row 2")
        val far = drawn(t).find(_.drop(4).toInt > 40).get
        rightClick(t, row(t, far).cells(0)) shouldBe true
        labels() shouldBe List("Delete row")
        fx(Modal.close())
        fx(t.selection()) shouldBe Set(3)
      }
    }
    "drop from the ⋮ at the end of the row the pointer is over" in {
      val t = table()
      var refs = Vector.empty[RowRef]
      fx(t.rowMenu @= Some((ref: RowRef) => List(MenuItem("Delete row", () => refs = refs :+ ref))))
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Grace").size.height() > 0.0) shouldBe true
        val button = fx(t.children().collectFirst {
          case c: Container if c.semantics().label == TableWords.rowActions => c
        }.get)
        fx(button.visible()) shouldBe false
        val ada = row(t, "Ada")
        fx {
          t.hovered @= true
          grid(t).event.pointer.move @= PointerEvent(PointerKind.Move, ada.cells(0).absoluteLeft + 2.0,
            ada.cells(0).absoluteTop + 2.0)
        }
        fx(button.visible()) shouldBe true
        // in the row's middle
        fx(button.absoluteTop + button.size.height() / 2.0) shouldBe
          fx(ada.absoluteTop + ada.size.height() / 2.0) +- 1.0
        fx(button.event.click @= PointerEvent(PointerKind.Click, button.absoluteLeft + 2.0, button.absoluteTop + 2.0))
        labels() shouldBe List("Delete row")
        choose("Delete row")
        refs shouldBe Vector(RowRef(2, Vector(2), None))
        fx(t.hovered @= false)
        fx(button.visible()) shouldBe false
      }
    }
    "not be listened for, nor its ⋮ made, on a table without one" in {
      val t = table()
      onScreen(t) {
        eventually(drawn(t).size == 4 && row(t, "Grace").size.height() > 0.0) shouldBe true
        fx(grid(t).event.pointer.claimsHold) shouldBe false
        fx(t.children().exists(c => c.semantics().label == TableWords.rowActions)) shouldBe false
        rightClick(t, row(t, "Grace").cells(0)) shouldBe false
        fx(grid(t).keepsEvent(KeyEvent(Key.ContextMenu, "ContextMenu"))) shouldBe false
      }
    }
  }
}
