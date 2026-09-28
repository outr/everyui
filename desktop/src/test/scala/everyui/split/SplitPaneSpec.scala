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
  }
}
