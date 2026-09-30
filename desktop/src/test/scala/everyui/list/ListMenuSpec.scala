package everyui.list

import everyui.component.{Container, Modal}
import everyui.event.{Pointer, PointerKind}
import everyui.focus.Focus
import everyui.menu.{Menu, MenuItem}
import everyui.{FxSpec, ui}
import reactify.*

/** A context menu asked for over a list is answered, and the platform's own kept away (a browser's Copy and Look Up),
  * only where the app puts a menu up for it: a list with no menu for the row, or a handler that shows none for it,
  * leaves the platform's. */
class ListMenuSpec extends FxSpec {
  private def numbers(n: Int): Var[Vector[String]] = Var((0 until n).map(i => "item " + i).toVector)

  // the secondary button over row `row`, as the platform hears it, and whether the app answered it
  private def rightClick(list: VirtualList[String], row: Int): Boolean = fx {
    Pointer.consumed = None
    list.event.pointer.heard(list.event.pointer.menu, PointerKind.Menu, 30.0, 28.0 * row + 3.0)
    Pointer.menuAnswered
  }

  private def onScreen[T](c: Container)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A list's context menu" should {
    "leave the platform's own where nothing listens for it, choosing the row all the same" in {
      val list = fx(VirtualList.text[String](numbers(10), s => s, s => s))
      onScreen(list) {
        rightClick(list, 4) shouldBe false
        fx(Modal.showing) shouldBe false
        fx(list.current()) shouldBe Some("item 4")
        fx(list.selection.keys()) shouldBe Set("item 4")
      }
    }
    "be answered where a menu is put up, and not where the handler shows none for the row" in {
      val list = fx(VirtualList.text[String](numbers(10), s => s, s => s))
      fx(list.menu.attach(p => if (p.item != "item 2") Menu.at(p.x, p.y, List(MenuItem("Open " + p.item)))))
      onScreen(list) {
        rightClick(list, 5) shouldBe true
        fx(Modal.showing) shouldBe true
        fx(Modal.close())
        rightClick(list, 2) shouldBe false
        fx(Modal.showing) shouldBe false
      }
    }
    "be answered by a handler that consumes it, and keep it from what the list sits in" in {
      val list = fx(VirtualList.text[String](numbers(10), s => s, s => s))
      val around = fx(new Container {
        size.width @= 400.0
        size.height @= 400.0
        children += list
      })
      var outer = 0
      fx(around.event.pointer.menu.attach(_ => outer += 1))
      fx(list.menu.attach(p => Menu.at(p.x, p.y, List(MenuItem("Open")))))
      onScreen(around) {
        // as a platform hears it, climbing: the list first, then what it sits in
        val answered = fx {
          Pointer.consumed = None
          list.event.pointer.heard(list.event.pointer.menu, PointerKind.Menu, 30.0, 31.0)
          around.event.pointer.heard(around.event.pointer.menu, PointerKind.Menu, 30.0, 31.0)
          Pointer.menuAnswered
        }
        answered shouldBe true
        outer shouldBe 0
      }
    }
  }
}
