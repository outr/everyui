package everyui.list

import everyui.component.Component
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.kit.TabBar
import everyui.tree.{Tree, TreeModel, TreeRow}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** What a pointer event carries (its button, the modifiers held with it) as lists, trees and tabs use it; the desktop's
  * names for the page and menu keys; and rows mirrored in a right-to-left language. */
class PointerSpec extends FxSpec {
  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double,
                    button: MouseButton = MouseButton.PRIMARY, shift: Boolean = false, ctrl: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, button, 1, shift, ctrl, false, false,
      button == MouseButton.PRIMARY, button == MouseButton.MIDDLE, button == MouseButton.SECONDARY, false, false,
      true, null))
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children ++= parts
      parts.head.focus()
    }
    try test finally fx {
      DragDrop.cancel()
      Direction.set(false)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def rowY(i: Int): Double = i * 28.0 + 10.0

  "A pointer event" should {
    "carry the modifiers held with it, which pick rows" in {
      val list = fx(VirtualList.text[String](Var((0 until 20).map("r" + _).toVector), s => s, s => s))
      onScreen(list) {
        mouse(list, MouseEvent.MOUSE_CLICKED, 40.0, rowY(2))
        mouse(list, MouseEvent.MOUSE_CLICKED, 40.0, rowY(5), shift = true)
        fx(list.selection.keys()) shouldBe Set("r2", "r3", "r4", "r5")
        mouse(list, MouseEvent.MOUSE_CLICKED, 40.0, rowY(9), ctrl = true)
        fx(list.selection.keys()) shouldBe Set("r2", "r3", "r4", "r5", "r9")
        fx(everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0, 0, ctrl = true).primary) shouldBe true
      }
    }
    "click only with the primary button, and drag only with it" in {
      val list = fx(VirtualList.text[String](Var((0 until 20).map("r" + _).toVector), s => s, s => s))
      fx {
        list.draggable @= true
        list.accepts @= (_ => true)
      }
      onScreen(list) {
        mouse(list, MouseEvent.MOUSE_CLICKED, 40.0, rowY(4), MouseButton.SECONDARY)
        mouse(list, MouseEvent.MOUSE_CLICKED, 40.0, rowY(4), MouseButton.MIDDLE)
        fx(list.current()) shouldBe Some("r0")
        mouse(list, MouseEvent.MOUSE_PRESSED, 40.0, rowY(1), MouseButton.MIDDLE)
        mouse(list, MouseEvent.MOUSE_DRAGGED, 40.0, rowY(6), MouseButton.MIDDLE)
        fx(DragDrop.dragging()) shouldBe None
        mouse(list, MouseEvent.MOUSE_RELEASED, 40.0, rowY(6), MouseButton.MIDDLE)
        mouse(list, MouseEvent.MOUSE_PRESSED, 40.0, rowY(1))
        mouse(list, MouseEvent.MOUSE_DRAGGED, 40.0, rowY(6))
        fx(DragDrop.dragging().map(_.keys)) shouldBe Some(List("r1"))
        mouse(list, MouseEvent.MOUSE_RELEASED, 40.0, rowY(6))
      }
    }
    "close a closable tab on a middle click" in {
      val current = Var("a")
      val bar = fx(new TabBar(current, 600.0, showOnFocus = false))
      var asked: List[String] = Nil
      onScreen(bar) {
        val (a, b) = fx((bar.add("a", "Alpha"), bar.add("b", "Beta", closable = true)))
        fx(bar.closing.attach(k => asked = asked :+ k))
        Seq(a, b).foreach { tab =>
          val x = fx(tab.absoluteLeft + 10.0)
          mouse(tab, MouseEvent.MOUSE_PRESSED, x, 10.0, MouseButton.MIDDLE)
          mouse(tab, MouseEvent.MOUSE_RELEASED, x, 10.0, MouseButton.MIDDLE)
        }
        asked shouldBe List("b")
        fx(current()) shouldBe "a"
      }
    }
  }

  "The desktop" should {
    "name the page keys and the menu key as the web does" in {
      val list = fx(VirtualList.text[String](Var((0 until 100).map("r" + _).toVector), s => s, s => s))
      var menus = 0
      fx(list.menu.attach(_ => menus += 1))
      onScreen(list) {
        press(KeyCode.PAGE_DOWN)
        fx(list.current()) shouldBe Some("r13")
        press(KeyCode.END)
        press(KeyCode.PAGE_UP)
        fx(list.current()) shouldBe Some("r86")
        press(KeyCode.HOME)
        fx(list.current()) shouldBe Some("r0")
        press(KeyCode.CONTEXT_MENU)
        menus shouldBe 1
      }
    }
  }

  "A right-to-left language" should {
    "mirror a tree's indentation, its chevrons and the scrollbar" in {
      val model = fx(new TreeModel[String](s => s))
      val tree = fx(new Tree[String](model, s => s))
      fx {
        model.setChildren(None, (0 until 30).map("/d" + _))
        model.setChildren(Some("/d0"), List("/d0/x"))
        tree.expand("/d0")
        Direction.set(true)
      }
      onScreen(tree) {
        fx(Direction.set(true))
        val top = fx(tree.list.rowAt(0).get.asInstanceOf[TreeRow[String]])
        val child = fx(tree.list.rowAt(1).get.asInstanceOf[TreeRow[String]])
        // the label's right edge in from the row's right by the indent, the child's further in
        def rightGap(r: TreeRow[String]): Double = fx(r.size.width() - r.label.position.left() - r.label.size.width())
        rightGap(child) - rightGap(top) shouldBe 16.0
        fx(tree.list.children().collectFirst { case c: everyui.component.Container if c.radius() == 3.0 => c.position.left() }) shouldBe Some(2.0)
        // a click at the chevron, now at the right, closes the folder
        val x = fx(tree.list.absoluteLeft + tree.list.size.width() - ListRow.inset - 4.0)
        mouse(tree.list, MouseEvent.MOUSE_CLICKED, x, rowY(0))
        fx(tree.expanded()) shouldBe Set.empty
        fx(tree.list.rowAt(0).get.asInstanceOf[TreeRow[String]].children().collectFirst {
          case i: everyui.component.MaterialIconView => i.icon().name
        }) shouldBe Some("chevron_left")
      }
    }
  }
}
