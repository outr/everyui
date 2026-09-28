package everyui.list

import everyui.component.Component
import everyui.focus.Focus
import everyui.tree.{Tree, TreeModel}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import reactify.*

/** Rows dragged with the pointer within a tree and between lists, landing only where the app accepts them, with the
  * place shown, folders opening under a drag and the rows scrolling near an end; Escape letting go; and the keyboard's
  * cut and paste. */
class DragSpec extends FxSpec {
  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double): Unit = fx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, kind != MouseEvent.MOUSE_RELEASED, false, false, false, false, false, null))
  }

  /** A drag on `from` from `x0`, `y0` to `x1`, `y1` in steps, not yet let go. */
  private def drag(from: Component, x0: Double, y0: Double, x1: Double, y1: Double): Unit = {
    mouse(from, MouseEvent.MOUSE_PRESSED, x0, y0)
    (1 to 4).foreach(s => mouse(from, MouseEvent.MOUSE_DRAGGED, x0 + (x1 - x0) * s / 4.0, y0 + (y1 - y0) * s / 4.0))
  }

  private def press(code: KeyCode, text: String = "", ctrl: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, ctrl, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, ctrl, false, false))
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children ++= parts
      parts.head.focus()
    }
    try test finally fx {
      DragDrop.cancel()
      DragDrop.held @= None
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def names(n: Int, prefix: String): Var[Vector[String]] = Var((0 until n).map(i => prefix + i).toVector)

  "A drag" should {
    "move rows between lists, landing only where the app accepts, with the place shown" in {
      val left = names(20, "a")
      val right = names(20, "b")
      val a = fx(VirtualList.text[String](left, s => s, s => s))
      val b = fx(VirtualList.text[String](right, s => s, s => s))
      var dropped: List[Drop] = Nil
      fx {
        b.position.left @= 400.0
        a.draggable @= true
        b.accepts @= (d => !d.target.contains("b5"))
        b.dropped.attach(d => dropped = dropped :+ d)
        a.press(1, everyui.event.Modifiers(false, false, false, false))
        a.press(3, everyui.event.Modifiers(false, false, false, true))
      }
      onScreen(a, b) {
        // from a chosen row, all the chosen rows go, the label says how many
        drag(a, 40.0, 28.0 * 2 + 10.0, 440.0, 28.0 * 4 + 20.0)
        fx(DragDrop.dragging().map(_.keys)) shouldBe Some(List("a1", "a2", "a3"))
        fx(b.dropHover()) shouldBe Some(Drop(a, List("a1", "a2", "a3"), Some("b4"), DropPosition.After))
        fx(b.rowAt(4).flatMap(_.drop())) shouldBe Some(DropPosition.After)
        // where the app will not take them, nothing shows
        mouse(a, MouseEvent.MOUSE_DRAGGED, 440.0, 28.0 * 5 + 5.0)
        fx(b.dropHover()) shouldBe None
        mouse(a, MouseEvent.MOUSE_DRAGGED, 440.0, 28.0 * 6 + 5.0)
        fx(b.dropHover().map(d => (d.target, d.position))) shouldBe Some((Some("b6"), DropPosition.Before))
        mouse(a, MouseEvent.MOUSE_RELEASED, 440.0, 28.0 * 6 + 5.0)
        dropped shouldBe List(Drop(a, List("a1", "a2", "a3"), Some("b6"), DropPosition.Before))
        fx(DragDrop.dragging()) shouldBe None
        fx(b.dropHover()) shouldBe None
        // a list that accepts nothing (the source, here) hears nothing
        drag(a, 40.0, 10.0, 40.0, 28.0 * 8 + 5.0)
        mouse(a, MouseEvent.MOUSE_RELEASED, 40.0, 28.0 * 8 + 5.0)
        dropped.size shouldBe 1
      }
    }
    "drop into a folder, which opens when held over, and scroll near an end" in {
      val model = fx(new TreeModel[String](s => s))
      val tree = fx(new Tree[String](model, s => s))
      var dropped: List[Drop] = Nil
      fx {
        model.setChildren(None, (0 until 40).map(i => "/f" + i))
        (0 until 40).foreach(i => model.setChildren(Some("/f" + i), List("/f" + i + "/x")))
        tree.draggable @= true
        tree.accepts @= (d => d.target.forall(t => !tree.inside(t, d.keys)))
        tree.dropped.attach(d => dropped = dropped :+ d)
      }
      onScreen(tree) {
        drag(tree.list, 60.0, 10.0, 60.0, 28.0 * 2 + 14.0)
        fx(tree.list.dropHover().map(d => (d.target, d.position))) shouldBe Some((Some("/f2"), DropPosition.Into))
        eventually(tree.expanded().contains("/f2")) shouldBe true
        // over itself, it cannot go
        mouse(tree.list, MouseEvent.MOUSE_DRAGGED, 60.0, 14.0)
        fx(tree.list.dropHover()) shouldBe None
        // near the foot, the rows scroll under it
        mouse(tree.list, MouseEvent.MOUSE_DRAGGED, 60.0, 395.0)
        eventually(tree.list.offset() > 100.0) shouldBe true
        mouse(tree.list, MouseEvent.MOUSE_DRAGGED, 60.0, 200.0)
        val over = fx(tree.list.dropHover().flatMap(_.target))
        mouse(tree.list, MouseEvent.MOUSE_RELEASED, 60.0, 200.0)
        dropped.map(d => (d.from, d.keys, d.target)) shouldBe List((tree, List("/f0"), over))
      }
    }
    "let go on Escape, dropping nothing" in {
      val a = fx(VirtualList.text[String](names(10, "a"), s => s, s => s))
      var dropped = 0
      fx {
        a.draggable @= true
        a.accepts @= (_ => true)
        a.dropped.attach(_ => dropped += 1)
      }
      onScreen(a) {
        drag(a, 40.0, 10.0, 40.0, 100.0)
        fx(DragDrop.dragging()).nonEmpty shouldBe true
        press(KeyCode.ESCAPE)
        fx(DragDrop.dragging()) shouldBe None
        mouse(a, MouseEvent.MOUSE_RELEASED, 40.0, 100.0)
        dropped shouldBe 0
      }
    }
  }

  "Cut and paste" should {
    "move rows from the keyboard, through the same rule" in {
      val a = fx(VirtualList.text[String](names(10, "a"), s => s, s => s))
      val b = fx(VirtualList.text[String](names(10, "b"), s => s, s => s))
      var dropped: List[Drop] = Nil
      fx {
        b.position.left @= 400.0
        b.accepts @= (d => d.from eq a)
        b.dropped.attach(d => dropped = dropped :+ d)
        a.selection.keys @= Set("a2", "a4")
        a.cut()
        a.paste() shouldBe false
        b.select("b7")
      }
      fx(b.paste()) shouldBe true
      dropped shouldBe List(Drop(a, List("a2", "a4"), Some("b7"), DropPosition.After))
      fx(DragDrop.held()) shouldBe None
    }
  }
}
