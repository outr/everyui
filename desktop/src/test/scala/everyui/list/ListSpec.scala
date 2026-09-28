package everyui.list

import everyui.event.Modifiers
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The virtual list: only the rows that show are made, the cursor and the selection move by keys and clicks and are
  * kept by key as the items change, and fifty thousand items scroll as quickly as a screenful. */
class ListSpec extends FxSpec {
  private val none = Modifiers(false, false, false, false)

  private def press(code: KeyCode, text: String = "", ctrl: Boolean = false, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, shift, ctrl, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, shift, ctrl, false, false))
  }

  private def numbers(n: Int): Var[Vector[String]] = Var((0 until n).map(i => "item " + i).toVector)

  private def onScreen[T](list: VirtualList[?])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
      list.focus()
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A virtual list" should {
    "make only the rows that show, whatever its length" in {
      val items = numbers(50000)
      val list = fx(VirtualList.text[String](items, s => s, s => s))
      onScreen(list) {
        fx(list.size.height @= 280.0)
        fx(list.count) shouldBe 50000
        val made = fx(list.rows.size)
        made should be <= 10 + 2 * 4 + 2
        fx(list.scrollTo(25000))
        fx(list.rowAt(25000).flatMap(_.item())) shouldBe Some("item 25000")
        fx(list.rows.size) shouldBe made
        fx(list.rowAt(25000).map(_.position.top())) shouldBe Some(0.0)
        // scrolled a pixel at a time through a thousand rows, and back to the top
        val started = System.nanoTime()
        fx((0 until 28000 by 7).foreach(y => list.scrollTop(y.toDouble)))
        val took = (System.nanoTime() - started) / 1e6
        info(f"4000 scroll steps over 50k rows: $took%.0f ms (${took / 4000.0}%.3f ms a step)")
        took should be < 4000.0
        fx(list.rows.size) shouldBe made
        val reindexed = System.nanoTime()
        fx(items @= items().updated(10, "changed"))
        info(f"50k items replaced and reindexed: ${(System.nanoTime() - reindexed) / 1e6}%.1f ms")
      }
    }
    "move the cursor with the arrows, extend with Shift, and leave at its ends" in {
      val list = fx(VirtualList.text[String](numbers(100), s => s, s => s))
      onScreen(list) {
        fx(list.current()) shouldBe Some("item 0")
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        fx(list.current()) shouldBe Some("item 2")
        fx(list.selection.keys()) shouldBe Set("item 2")
        press(KeyCode.DOWN, shift = true)
        press(KeyCode.DOWN, shift = true)
        fx(list.selection.keys()) shouldBe Set("item 2", "item 3", "item 4")
        press(KeyCode.END)
        fx(list.current()) shouldBe Some("item 99")
        fx(list.offset()) shouldBe (100 * 28.0 - 400.0)
        press(KeyCode.HOME)
        fx(list.current()) shouldBe Some("item 0")
        fx(list.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowUp, "ArrowUp"))) shouldBe false
        press(KeyCode.A, "a", ctrl = true)
        fx(list.selection.keys().size) shouldBe 100
        // a letter jumps to the next label starting with it
        press(KeyCode.I, "i")
        fx(list.current()) shouldBe Some("item 1")
      }
    }
    "pick rows by clicks: alone, a range with Shift, one more with Primary" in {
      val list = fx(VirtualList.text[String](numbers(100), s => s, s => s))
      var activated: List[String] = Nil
      fx(list.activated.attach(s => activated = activated :+ s))
      onScreen(list) {
        fx(list.press(3, none))
        fx(list.press(6, none.copy(shift = true)))
        fx(list.selection.keys()) shouldBe Set("item 3", "item 4", "item 5", "item 6")
        fx(list.press(10, none.copy(ctrl = true)))
        fx(list.selection.keys()) shouldBe Set("item 3", "item 4", "item 5", "item 6", "item 10")
        fx(list.press(10, none.copy(ctrl = true)))
        fx(list.selection.keys()) shouldBe Set("item 3", "item 4", "item 5", "item 6")
        fx(list.press(8, none))
        fx(list.press(8, none))
        activated shouldBe List("item 8")
        press(KeyCode.ENTER)
        activated shouldBe List("item 8", "item 8")
        // a click on a row picks it through the pointer too
        fx(list.clickAt(list.absoluteLeft + 20.0, list.absoluteTop + 28.0 * 2 + 5.0, none))
        fx(list.current()) shouldBe Some("item 2")
      }
    }
    "keep the cursor, the selection and the scroll by key as items come and go" in {
      val items = numbers(1000)
      val list = fx(VirtualList.text[String](items, s => s, s => s))
      onScreen(list) {
        fx {
          list.scrollTo(500)
          list.select("item 510")
        }
        val offset = fx(list.offset())
        fx(items @= Vector("new a", "new b") ++ items())
        fx(list.offset()) shouldBe offset + 56.0
        fx(list.current()) shouldBe Some("item 510")
        fx(list.selection.keys()) shouldBe Set("item 510")
        fx(list.rowAt(list.indexOf("item 510")).flatMap(_.item())) shouldBe Some("item 510")
        // the row under the cursor gone, the cursor stays where it was
        fx(items @= items().filterNot(_ == "item 510"))
        fx(list.current()) shouldBe Some("item 511")
      }
    }
    "place rows of their own heights" in {
      val items = Var(Vector("a", "b", "c"))
      val list = fx(new VirtualList[String](items, s => s, s => s, measured = true)(() => new ListRow[String] {
        size.height := (if (item().contains("b")) 60.0 else 20.0)
      }))
      onScreen(list) {
        eventually(list.total == 100.0) shouldBe true
        fx(list.top(2)) shouldBe 80.0
        fx(list.indexAt(70.0)) shouldBe 1
      }
    }
    "ask for its menu on the menu key and on a held OK" in {
      val list = fx(VirtualList.text[String](numbers(10), s => s, s => s))
      var asked: List[String] = Nil
      fx(list.menu.attach(p => asked = asked :+ p.key))
      onScreen(list) {
        press(KeyCode.DOWN)
        press(KeyCode.F10, shift = true)
        asked shouldBe List("item 1")
        fx(javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)))
        eventually(asked.size == 2) shouldBe true
        var activated = 0
        fx(list.activated.attach(_ => activated += 1))
        fx(javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER, false, false, false, false)))
        activated shouldBe 0
      }
    }
    "choose one row on a remote, following the cursor" in {
      val list = fx(VirtualList.text[String](numbers(10), s => s, s => s))
      onScreen(list) {
        fx(list.selection.multiple @= false)
        press(KeyCode.DOWN, shift = true)
        fx(list.selection.keys()) shouldBe Set("item 1")
        fx(list.press(4, none.copy(ctrl = true)))
        fx(list.selection.keys()) shouldBe Set("item 4")
      }
    }
  }
}
