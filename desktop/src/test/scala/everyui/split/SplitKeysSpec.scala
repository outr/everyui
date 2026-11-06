package everyui.split

import everyui.component.*
import everyui.component.types.SemanticRange
import everyui.event.{KeyEvent, Keys}
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import reactify.*

/** A split's handle and a resize handle are reached by the keys in reading order and moved by them: a step, four,
  * either end, and said to a screen reader as a divider where it stands. */
class SplitKeysSpec extends FxSpec {
  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def press(c: Component, name: String, shift: Boolean = false): Unit =
    fx(c.event.key.down @= KeyEvent(Keys.of(name), name, shift = shift))

  "A split's handle" should {
    "come between its sides in Tab's order" in {
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b))
      onScreen(split) {
        fx(Focus.candidates(split).toList) shouldBe List(a, split.handle, b)
      }
    }
    "move a step with the arrows, four with Shift or a page, and to either end with Home and End" in {
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, ratio = Var(0.5)))
      onScreen(split) {
        fx {
          split.size.width @= 1006.0
          split.size.height @= 600.0
          split.handle.focus()
        }
        fx(a.size.width()) shouldBe 500.0
        press(split.handle, "ArrowRight")
        fx(a.size.width()) shouldBe 524.0
        press(split.handle, "ArrowLeft", shift = true)
        fx(a.size.width()) shouldBe 428.0
        press(split.handle, "PageDown")
        fx(a.size.width()) shouldBe 524.0
        press(split.handle, "End")
        fx(a.size.width()) shouldBe 880.0
        press(split.handle, "Home")
        fx(a.size.width()) shouldBe 120.0
      }
    }
    "be a divider to a screen reader, saying where it stands" in {
      val (a, b) = fx((card(), card()))
      val split = fx(new SplitPane(a, b, ratio = Var(0.25)))
      onScreen(split) {
        fx {
          split.size.width @= 1006.0
          split.size.height @= 600.0
        }
        fx(split.handle.semantics().role) shouldBe "separator"
        fx(everyui.access.Access.nameOf(split.handle)) shouldBe "Divider"
        fx(split.handle.semantics().range) shouldBe Some(SemanticRange(25.0, 0.0, 100.0, "25%"))
      }
    }
  }

  "A resize handle" should {
    "take its part to its least and its most with Home and End, and a step each way with the arrows" in {
      val width = Var(280.0)
      val edge = fx(new ResizeHandle(width, min = 180.0, max = 480.0))
      onScreen(edge) {
        fx(edge.focusable()) shouldBe true
        press(edge, "End")
        width() shouldBe 480.0
        press(edge, "Home")
        width() shouldBe 180.0
        press(edge, "ArrowRight")
        width() shouldBe 204.0
        fx(edge.semantics().range) shouldBe Some(SemanticRange(204.0, 180.0, 480.0, "204 px"))
      }
    }
  }
}
