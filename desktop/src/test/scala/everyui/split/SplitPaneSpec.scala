package everyui.split

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.keymap.{Chord, Keymap}
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A split: its sides placed by the ratio and kept to their minimums, the handle dragged, a side folded by a
  * double-click and by a command, the arrows across, and one split inside another. */
class SplitPaneSpec extends FxSpec {
  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c
  }

  private def pointer(c: Component, kind: PointerKind, x: Double, y: Double): Unit = {
    val e = PointerEvent(kind, x, y)
    kind match {
      case PointerKind.Down => c.event.pointer.down @= e
      case PointerKind.Move => c.event.pointer.move @= e
      case PointerKind.Up => c.event.pointer.up @= e
      case _ => c.event.click @= e
    }
  }

  /** `split` on the screen, 1006 by 600 (1000 to share, less the handle), and taken away after. */
  private def onScreen[T](split: SplitPane)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      split.size.width @= 1006.0
      split.size.height @= 600.0
      ui.children += split
    }
    try test finally fx {
      Keymap.global.commands @= Nil
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A split" should {
    "share the room by its ratio, keeping each side to its minimum" in {
      val ratio = Var(0.3)
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, ratio = ratio))
      onScreen(split) {
        fx((a.size.width(), b.size.width(), b.size.height())) shouldBe (300.0, 700.0, 600.0)
        fx(split.handle.position.left()) shouldBe 300.0
        fx(ratio @= 0.01)
        fx(a.size.width()) shouldBe 120.0
        fx(ratio @= 0.99)
        fx(b.size.width()) shouldBe 120.0
      }
    }
    "follow the handle dragged, and keep the ratio it was left at" in {
      val ratio = Var(0.5)
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, ratio = ratio))
      onScreen(split) {
        fx {
          pointer(split.handle, PointerKind.Down, 503.0, 10.0)
          pointer(split.handle, PointerKind.Move, 403.0, 10.0)
        }
        fx(ui.children().size) shouldBe 2   // the catcher over the screen, while it drags
        fx(pointer(ui.children().last, PointerKind.Move, 303.0, 10.0))
        fx(pointer(ui.children().last, PointerKind.Up, 303.0, 10.0))
        fx(ui.children().size) shouldBe 1
        ratio() shouldBe 0.3 +- 0.0001
        fx(a.size.width()) shouldBe 300.0 +- 0.01
      }
    }
    "fold its collapsible side on a double-click, or dragged past it, and bring it back" in {
      val ratio = Var(0.3)
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, ratio = ratio, collapsible = Some(SplitSide.First)))
      onScreen(split) {
        fx(a.focus())
        fx {
          pointer(split.handle, PointerKind.Click, 303.0, 10.0)
          pointer(split.handle, PointerKind.Click, 303.0, 10.0)
        }
        fx(split.collapsed()) shouldBe true
        fx((a.size.width(), b.size.width())) shouldBe (0.0, 1000.0)
        // the focus is not left on the side folded away
        fx(Focus.focused()) shouldBe Some(b)
        fx(split.toggle())
        fx(a.size.width()) shouldBe 300.0
        fx {
          pointer(split.handle, PointerKind.Down, 303.0, 10.0)
          pointer(split.handle, PointerKind.Move, 40.0, 10.0)
          pointer(split.handle, PointerKind.Up, 40.0, 10.0)
        }
        fx(split.collapsed()) shouldBe true
        ratio() shouldBe 0.3 +- 0.0001
      }
    }
    "move by its commands, and hand the arrows across" in {
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, ratio = Var(0.5)))
      onScreen(split) {
        fx {
          Keymap.global.add("split.grow", "Wider", Chord("ArrowRight", primary = true, alt = true))(split.grow())
          javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, true, true, false))
          javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.RIGHT, false, false, false, false))
        }
        fx(a.size.width()) shouldBe 524.0
        fx(a.focus())
        fx(Focus.key("ArrowRight", true))
        fx(Focus.focused()) shouldBe Some(b)
        fx(Focus.key("ArrowLeft", true))
        fx(Focus.focused()) shouldBe Some(a)
      }
    }
    "put its first side on the right in a right-to-left language, and mirror the drag and the arrows" in {
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, ratio = Var(0.3)))
      fx(Direction.rtl @= true)
      try onScreen(split) {
        fx((a.parent().get.position.left(), split.handle.position.left(), b.parent().get.position.left())) shouldBe
          (706.0, 700.0, 0.0)
        fx {
          pointer(split.handle, PointerKind.Down, 703.0, 10.0)
          pointer(split.handle, PointerKind.Move, 603.0, 10.0)
          pointer(split.handle, PointerKind.Up, 603.0, 10.0)
        }
        fx(a.size.width()) shouldBe 400.0 +- 0.01
        fx(a.focus())
        fx(Focus.key("ArrowLeft", true))
        fx(Focus.focused()) shouldBe Some(b)
        fx(Focus.key("ArrowRight", true))
        fx(Focus.focused()) shouldBe Some(a)
      }
      finally fx(Direction.rtl @= false)
    }
    "hold another split, which follows its side" in {
      val (a, b, c) = fx((card(), card(), card()))
      val inner = fx(new SplitPane(b, c, vertical = true, ratio = Var(0.25)))
      val split = fx(new SplitPane(a, inner, ratio = Var(0.4)))
      onScreen(split) {
        fx((inner.size.width(), inner.size.height())) shouldBe (600.0, 600.0)
        fx((b.size.height(), c.size.height(), c.size.width())) shouldBe (148.5, 445.5, 600.0)
        fx(split.ratio @= 0.5)
        fx(c.size.width()) shouldBe 500.0
        // Down within the inner split, Left out of it to the outer's first side
        fx(b.focus())
        fx(Focus.key("ArrowDown", true))
        fx(Focus.focused()) shouldBe Some(c)
        fx(Focus.key("ArrowLeft", true))
        fx(Focus.focused()) shouldBe Some(a)
      }
    }
    "keep a side at its length in pixels as the split changes, the other side taking the change" in {
      val width = Var(280.0)
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, length = Some(SplitSide.First -> width)))
      onScreen(split) {
        fx((a.size.width(), b.size.width())) shouldBe (280.0, 720.0)
        fx(split.ratio @= 0.9)
        fx(a.size.width()) shouldBe 280.0
        // the window made narrower and wider: the first side keeps its width
        fx(split.size.width @= 806.0)
        fx((a.size.width(), b.size.width())) shouldBe (280.0, 520.0)
        fx(split.size.width @= 1406.0)
        fx((a.size.width(), b.size.width())) shouldBe (280.0, 1120.0)
        // too narrow for both, the second side's minimum holds, and the width kept comes back as it widens
        fx(split.size.width @= 306.0)
        fx((a.size.width(), b.size.width())) shouldBe (180.0, 120.0)
        width() shouldBe 280.0
        fx(split.size.width @= 1006.0)
        fx(a.size.width()) shouldBe 280.0
        // a drag writes the width, kept to the first side's most
        fx(split.maxFirst @= 400.0)
        fx(pointer(split.handle, PointerKind.Down, 283.0, 10.0))
        fx(pointer(split.handle, PointerKind.Move, 343.0, 10.0))
        fx(pointer(split.handle, PointerKind.Up, 343.0, 10.0))
        width() shouldBe 340.0
        fx(pointer(split.handle, PointerKind.Down, 343.0, 10.0))
        fx(pointer(split.handle, PointerKind.Move, 643.0, 10.0))
        fx(pointer(split.handle, PointerKind.Up, 643.0, 10.0))
        width() shouldBe 400.0
        fx(a.size.width()) shouldBe 400.0
        // the most holds as the app sets it, too
        fx(width @= 900.0)
        fx(a.size.width()) shouldBe 400.0
      }
    }
    "keep its second side at its length, folding it by a double-click and bringing it back as it was" in {
      val width = Var(300.0)
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, length = Some(SplitSide.Second -> width), collapsible = Some(SplitSide.Second)))
      onScreen(split) {
        fx((a.size.width(), b.size.width())) shouldBe (700.0, 300.0)
        fx(split.size.width @= 1206.0)
        fx((a.size.width(), b.size.width())) shouldBe (900.0, 300.0)
        fx(split.minSecond @= 160.0)
        fx(split.maxSecond @= 520.0)
        // the second side grows as the handle goes back
        fx(pointer(split.handle, PointerKind.Down, 903.0, 10.0))
        fx(pointer(split.handle, PointerKind.Move, 503.0, 10.0))
        fx(pointer(split.handle, PointerKind.Up, 503.0, 10.0))
        width() shouldBe 520.0
        fx(split.collapsed()) shouldBe false
        // (each click pressed and let go where it is, as a pointer does)
        for (_ <- 1 to 2) {
          fx(pointer(split.handle, PointerKind.Down, 683.0, 10.0))
          fx(pointer(split.handle, PointerKind.Up, 683.0, 10.0))
          fx(pointer(split.handle, PointerKind.Click, 683.0, 10.0))
        }
        fx(split.collapsed()) shouldBe true
        fx((a.size.width(), b.size.width())) shouldBe (1200.0, 0.0)
        fx(split.toggle())
        fx(b.size.width()) shouldBe 520.0
        // and a pixel side is mirrored right to left as a ratio's is
        fx(Direction.set(true))
        fx((a.parent().get.position.left(), b.parent().get.position.left())) shouldBe (526.0, 0.0)
        fx(pointer(split.handle, PointerKind.Down, 523.0, 10.0))
        fx(pointer(split.handle, PointerKind.Move, 423.0, 10.0))
        fx(pointer(split.handle, PointerKind.Up, 423.0, 10.0))
        width() shouldBe 420.0
        fx(Direction.set(false))
      }
    }
  }
}
