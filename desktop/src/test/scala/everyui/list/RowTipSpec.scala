package everyui.list

import everyui.component.{Component, Container, Modal, TextView}
import everyui.event.{Pointer, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.menu.{Menu, MenuItem}
import everyui.tooltip.Tooltip
import everyui.tree.{Tree, TreeModel, TreeRow}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** A row's tip (`Decoration.tooltip`): shown by `Tooltip` after its delay as the pointer rests on the row, as the cursor
  * rests on it in a list with the focus, and at once as a finger is held on it where that opens no menu; the words of
  * the item the row shows as it shows, never one it showed before; gone as the rows scroll, as the row is handed
  * another item and with the list; the row's description to a screen reader; a long path wrapped within the tip; and
  * rows without one made, and left, as they were. */
class RowTipSpec extends FxSpec {
  private val wide = "src/main/scala/ai/voidcraft/ui/sidebar/conversations/blocks/rendering/internal/support/" +
    "AFileNameLongEnoughToRunPastAnyTip.scala"

  // every third item has no tip; item 7's is far wider than a tip
  private def path(i: Int): String = if (i == 7) wide else "src/main/scala/folder" + (i % 10).toString + "/File" + i.toString + ".scala"
  private def tipOf(i: Int): Option[String] = if (i % 3 == 2) None else Some(path(i))
  private def files(n: Int): Var[Vector[Int]] = Var((0 until n).toVector)

  private def made(n: Int, tips: Boolean = true): VirtualList[Int] = {
    val list = VirtualList.text[Int](files(n), i => i.toString, i => "File" + i.toString,
      i => if (tips) Decoration(badge = "folder", tooltip = tipOf(i)) else Decoration(badge = "folder"))
    list.size.width @= 320.0
    list.size.height @= 280.0
    list.position.left @= 100.0
    list.position.top @= 100.0
    list
  }

  // the views made for rows' tips, newest first
  private var views: List[Component] = Nil

  /** A list whose rows each have a view of their own for their tip, saying their item, and (with `words`) the words of
    * `tipOf` too. */
  private def viewed(n: Int, words: Boolean = true): VirtualList[Int] = {
    views = Nil
    val list = new VirtualList[Int](files(n), i => i.toString, i => "File" + i.toString)(() =>
      new TextRow[Int](i => "File" + i.toString, i => Decoration(tooltip = if (words) tipOf(i) else None)) {
        tipView @= Some { _ =>
          val view = new TextView {
            content @= "View " + item().map(_.toString).getOrElse("")
          }
          views = view :: views
          view
        }
      })
    list.size.width @= 320.0
    list.size.height @= 280.0
    list.position.left @= 100.0
    list.position.top @= 100.0
    list
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      Tooltip.delay @= 0.05
      Tooltip.onFocus @= true
      Tooltip.hide()
      Pointer.usedAt = -1.0e9
      ui.children.clear()
      Focus.focused @= None
      ui.children += c
    }
    try test finally fx {
      Modal.close()
      Tooltip.hide()
      Tooltip.delay @= 0.6
      Tooltip.onFocus @= true
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)
  private def rowOf(list: VirtualList[Int], i: Int): ListRow[Int] = onFx(list.rowAt(i).get)
  private def enter(row: ListRow[?]): Unit = fx(row.event.pointer.enter @= PointerEvent(PointerKind.Enter, row.absoluteLeft + 20.0,
    row.absoluteTop + 5.0))
  private def leave(row: ListRow[?]): Unit = fx(row.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0))
  // the tip showing and its words: the last thing on the screen
  private def plate: Option[Container] = ui.children().lastOption.filter(_ => Tooltip.shownFor.nonEmpty)
    .collect { case c: Container => c }
  private def words: Option[String] = plate.flatMap(_.children().headOption).collect { case t: TextView => t.content().replace("\u200B", "") }
  private def shownFor(row: Component): Boolean = Tooltip.shownFor.exists(_ eq row)

  private def key(code: KeyCode): Unit = List(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED).foreach { kind =>
    fx(javafx.event.Event.fireEvent(window, new KeyEvent(kind, "", "", code, false, false, false, false)))
  }

  // a finger held on row `i`, as the platform hears it: the list's menu asked for there
  private def hold(list: VirtualList[Int], i: Int): Unit = fx {
    Pointer.consumed = None
    list.event.pointer.heard(list.event.pointer.menu, PointerKind.Menu, list.absoluteLeft + 30.0,
      list.absoluteTop + list.top(i) - list.offset() + 5.0)
  }

  "A row's tip" should {
    "show after the pointer rests on the row, not before, as a tooltip does, and go as it leaves" in {
      val list = fx(made(20))
      onScreen(list) {
        val r = rowOf(list, 3)
        fx(Tooltip.delay @= 0.3)
        val entered = System.nanoTime()
        enter(r)
        eventually(shownFor(r)) shouldBe true
        // not before its delay: timed from before the pointer came, which a busy machine can only make longer, where a
        // look a moment after it came could be past the delay on one
        (System.nanoTime() - entered) / 1e9 should be >= 0.3
        fx(words) shouldBe Some(path(3))
        // the tooltip's own look and place: its plate, under the row and centred on it
        fx(plate.get.backgroundColor()) shouldBe fx(everyui.theme.Theme.raised())
        fx(plate.get.radius()) shouldBe 6.0
        fx(plate.get.position.top()) shouldBe fx(r.absoluteTop + r.size.height() + 6.0)
        fx(plate.get.position.left() + plate.get.size.width() / 2.0) shouldBe fx(r.absoluteLeft + r.size.width() / 2.0) +- 0.5
        leave(r)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "show nothing for a row without one, nor for the pointer passing over" in {
      val list = fx(made(20))
      onScreen(list) {
        val none = rowOf(list, 2)
        enter(none)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
        val r = rowOf(list, 4)
        enter(r)
        leave(r)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "show the words of the item a reused row shows now, over a long list scrolled" in {
      val list = fx(made(5000))
      onScreen(list) {
        val first = rowOf(list, 0)
        fx(list.scrollTop(28.0 * 3000.0))
        // the rows made at the top are handed items far down the list
        val at = fx(first.index())
        at should be >= 2990
        fx(first.item()) shouldBe Some(at)
        fx(list.rows.size) should be < 30
        for (i <- List(at, 3001, 3003, 3004)) {
          val r = rowOf(list, i)
          enter(r)
          eventually(shownFor(r) && words.contains(tipOf(i).get)) shouldBe true
          leave(r)
        }
        // and back at the top, the same row the words of the first again
        fx(list.scrollTop(0.0))
        val again = rowOf(list, 0)
        enter(again)
        eventually(shownFor(again) && words.contains(path(0))) shouldBe true
      }
    }
    "go as the row resting under the pointer is handed another item, and come with that item's words" in {
      val list = fx(made(20))
      onScreen(list) {
        val r = rowOf(list, 4)
        enter(r)
        eventually(shownFor(r) && words.contains(path(4))) shouldBe true
        // the items again, in the other order: the row at the same place shows another
        fx(list.items @= (0 until 20).reverse.toVector)
        fx(r.item()) shouldBe Some(15)
        fx(words.contains(path(4))) shouldBe false
        eventually(shownFor(r) && words.contains(path(15))) shouldBe true
        // handed one without a tip, it shows none
        fx(list.items @= (0 until 20).map(i => if (i == 4) 17 else if (i == 17) 4 else i).toVector)
        fx(r.item()) shouldBe Some(17)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "go as the rows scroll, and with the list" in {
      val list = fx(made(200))
      onScreen(list) {
        val r = rowOf(list, 6)
        enter(r)
        eventually(shownFor(r)) shouldBe true
        fx(list.scrollBy(56.0))
        fx(Tooltip.shownFor) shouldBe None
        leave(r)
        val s = rowOf(list, 9)
        enter(s)
        eventually(shownFor(s)) shouldBe true
        fx(list.dispose())
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "be the row's description, and change with the item it shows" in {
      val list = fx(made(5000))
      onScreen(list) {
        fx(rowOf(list, 0).accessibleDescription()) shouldBe path(0)
        val first = rowOf(list, 0)
        fx(list.scrollTop(28.0 * 1500.0))
        fx(first.accessibleDescription()) shouldBe fx(first.item().flatMap(tipOf).getOrElse(""))
        val i = fx((1500 until 1520).find(i => list.rowAt(i).exists(r => r.item().exists(j => tipOf(j).isEmpty))).get)
        fx(rowOf(list, i).accessibleDescription()) shouldBe ""
      }
    }
    "show for the cursor, after the delay, as the keys move it in a list with the focus" in {
      val list = fx(made(20))
      onScreen(list) {
        fx(list.focus())
        fx(list.current()) shouldBe Some("0")
        eventually(shownFor(rowOf(list, 0)) && words.contains(path(0))) shouldBe true
        key(KeyCode.DOWN)
        eventually(shownFor(rowOf(list, 1)) && words.contains(path(1))) shouldBe true
        // onto a row without one: none
        key(KeyCode.DOWN)
        fx(list.current()) shouldBe Some("2")
        fx(Tooltip.shownFor) shouldBe None
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
        key(KeyCode.DOWN)
        eventually(shownFor(rowOf(list, 3))) shouldBe true
        // the focus gone, so is the tip
        fx(Focus.focused @= None)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "follow the cursor down past the rows in view, and not show for it where the focus shows no tips" in {
      val list = fx(made(200))
      onScreen(list) {
        fx(list.focus())
        (0 until 13).foreach(_ => key(KeyCode.DOWN))
        fx(list.current()) shouldBe Some("13")
        fx(list.offset()) should be > 0.0
        eventually(shownFor(rowOf(list, 13)) && words.contains(path(13))) shouldBe true
        fx(Tooltip.onFocus @= false)
        key(KeyCode.DOWN)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "show at once for a finger held on a row where that opens no menu, for a while, and not where it opens one" in {
      val list = fx(made(20))
      onScreen(list) {
        fx(list.holdTips @= Some(true))
        hold(list, 4)
        fx(shownFor(rowOf(list, 4))) shouldBe true
        fx(words) shouldBe Some(path(4))
        eventually(Tooltip.shownFor.isEmpty) shouldBe true
        // a menu for the row: it opens, and no tip
        var menus = true
        fx(list.menu.attach(p => if (menus) Menu.at(p.x, p.y, List(MenuItem("Open " + p.item.toString)))))
        hold(list, 5)
        fx(Modal.showing) shouldBe true
        fx(Tooltip.shownFor) shouldBe None
        // nor one for the cursor it moved, under the menu
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
        fx(Modal.close())
        // where a finger is not what holds (the secondary button), no tip either
        fx(list.holdTips @= Some(false))
        menus = false
        hold(list, 6)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "wrap a long path within the tip" in {
      val list = fx(made(20))
      onScreen(list) {
        val r = rowOf(list, 7)
        enter(r)
        eventually(shownFor(r) && words.contains(wide)) shouldBe true
        val text = fx(plate.get.children().head)
        fx(text.size.width()) shouldBe fx(Tooltip.widest())
        fx(text.size.height()) should be > 30.0
        fx(plate.get.size.width()) shouldBe fx(Tooltip.widest() + 20.0)
      }
    }
    "show a tree's node's, and none for one without" in {
      val model = new TreeModel[String](p => p)
      val tree = fx {
        val t = new Tree[String](model, p => p.substring(p.lastIndexOf('/') + 1))
        t.decorate @= (p => if (p.endsWith(".scala")) Decoration(tooltip = Some(p)) else Decoration.plain)
        model.setChildren(None, Vector("src", "build.sbt"))
        model.setChildren(Some("src"), Vector("src/main/A.scala", "src/main/B.scala"))
        t.expanded @= Set("src")
        t.size.width @= 300.0
        t.size.height @= 200.0
        t
      }
      onScreen(tree) {
        val folder = fx(tree.list.rowAt(0).get)
        val file = fx(tree.list.rowAt(2).get)
        fx(file.isInstanceOf[TreeRow[?]]) shouldBe true
        enter(folder)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
        leave(folder)
        enter(file)
        eventually(shownFor(file) && words.contains("src/main/B.scala")) shouldBe true
        fx(file.accessibleDescription()) shouldBe "src/main/B.scala"
      }
    }
    "leave rows without one as they were: none watched, no description, the same children" in {
      val plain = fx(made(20, tips = false))
      val tipped = fx(made(20))
      onScreen(plain) {
        val r = rowOf(plain, 3)
        fx(r.tip()) shouldBe None
        fx(r.children().size) shouldBe fx(rowOf(tipped, 3).children().size)
        enter(r)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
        fx(plain.focus())
        key(KeyCode.DOWN)
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "leave a press and a click on its row to the list, which chooses the row, as on a row without one" in {
      // (on Android the row's press, heard to hide its tip, held the whole touch, and the list never heard the tap)
      val list = fx(made(20))
      onScreen(list) {
        for (i <- List(4, 7, 5)) {
          val r = rowOf(list, i)
          fx(r.tip().nonEmpty) shouldBe (i != 5)
          val x = fx(r.absoluteLeft + 40.0)
          val y = fx(r.absoluteTop + 5.0)
          List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).foreach(kind => fx(javafx.event.Event.fireEvent(r.node.fx,
            new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, true, null))))
          fx(list.selection.keys()) shouldBe Set(i.toString)
        }
      }
    }
    "sit under what shows of a row half past the foot of its list" in {
      val list = fx(made(20))
      onScreen(list) {
        // ten rows and a half: the eleventh shows its top half
        fx(list.scrollTop(14.0))
        val r = rowOf(list, 10)
        fx(r.absoluteTop + r.size.height()) should be > fx(list.absoluteTop + list.size.height())
        enter(r)
        eventually(shownFor(r)) shouldBe true
        fx(plate.get.position.top()) shouldBe fx(list.absoluteTop + list.size.height() + 6.0) +- 0.5
      }
    }
    "show a row's own view in place of its words, for the pointer, its words then only its description" in {
      val list = fx(viewed(20))
      onScreen(list) {
        val r = rowOf(list, 3)
        enter(r)
        eventually(shownFor(r) && words.contains("View 3")) shouldBe true
        // and not its words after it, asked for on the same pointer
        Thread.sleep(250L)
        fx(words) shouldBe Some("View 3")
        fx(views.size) shouldBe 1
        fx(r.accessibleDescription()) shouldBe path(3)
        leave(r)
        fx(Tooltip.shownFor) shouldBe None
        fx(views.forall(_.takenApart)) shouldBe true
        // a row with a view and no words shows its view
        val none = rowOf(list, 2)
        enter(none)
        eventually(shownFor(none) && words.contains("View 2")) shouldBe true
        leave(none)
      }
    }
    "show a row's own view for the keys' cursor, following it" in {
      val list = fx(viewed(20))
      onScreen(list) {
        fx(list.focus())
        eventually(shownFor(rowOf(list, 0)) && words.contains("View 0")) shouldBe true
        key(KeyCode.DOWN)
        eventually(shownFor(rowOf(list, 1)) && words.contains("View 1")) shouldBe true
        key(KeyCode.DOWN)
        eventually(shownFor(rowOf(list, 2)) && words.contains("View 2")) shouldBe true
        fx(Focus.focused().exists(_ eq list)) shouldBe true
        fx(Focus.focused @= None)
        fx(Tooltip.shownFor) shouldBe None
        fx(views.forall(_.takenApart)) shouldBe true
      }
    }
    "show a row's own view at once for a finger held on it, with or without words, made once, for a while" in {
      for (withWords <- List(false, true)) {
        val list = fx(viewed(20, withWords))
        onScreen(list) {
          fx(list.holdTips @= Some(true))
          hold(list, 4)
          fx(shownFor(rowOf(list, 4))) shouldBe true
          fx(words) shouldBe Some("View 4")
          // the row's own watch does not make it again for the same hold
          Thread.sleep(150L)
          fx(views.size) shouldBe 1
          fx(words) shouldBe Some("View 4")
          eventually(Tooltip.shownFor.isEmpty) shouldBe true
          fx(views.forall(_.takenApart)) shouldBe true
        }
      }
    }
    "leave a tap on a row with a view of its own to the list, which chooses it, and a hold to the list, which shows it" in {
      // (on Android the row's watch for a hold, to show its view, took every touch on it: Voidcraft's workspace
      // switcher's rows were dead to taps)
      val list = fx(viewed(20))
      onScreen(list) {
        for (i <- List(4, 7)) {
          val r = rowOf(list, i)
          fx(r.tipDrawn) shouldBe true
          fx(r.event.pointer.claimsHold) shouldBe false
          val x = fx(r.absoluteLeft + 40.0)
          val y = fx(r.absoluteTop + 5.0)
          List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).foreach(kind => fx(javafx.event.Event.fireEvent(r.node.fx,
            new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, true, null))))
          fx(list.selection.keys()) shouldBe Set(i.toString)
        }
        fx(list.holdTips @= Some(true))
        hold(list, 5)
        fx(shownFor(rowOf(list, 5))) shouldBe true
        fx(words) shouldBe Some("View 5")
      }
    }
    "leave nothing of a row's view alive once it closes, over rows handed other items" in {
      val list = fx(viewed(200))
      onScreen(list) {
        val r = rowOf(list, 5)
        enter(r)
        eventually(shownFor(r)) shouldBe true
        leave(r)
        val before = fx(Component.liveNow)
        for (i <- List(5, 6, 7)) {
          val s = rowOf(list, i)
          enter(s)
          eventually(shownFor(s) && words.contains("View " + i.toString)) shouldBe true
          leave(s)
        }
        // handed another item under the pointer: its view goes, and the new item's comes
        enter(r)
        eventually(shownFor(r) && words.contains("View 5")) shouldBe true
        fx(list.items @= (0 until 200).reverse.toVector)
        eventually(shownFor(r) && words.contains("View " + r.item().get.toString)) shouldBe true
        leave(r)
        fx(Tooltip.shownFor) shouldBe None
        fx(views.forall(_.takenApart)) shouldBe true
        fx(list.items @= (0 until 200).toVector)
        fx(Component.liveNow) shouldBe before
      }
    }
  }
}
