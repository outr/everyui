package everyui.tooltip

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}

/** Tips: after the pointer rests or the focus stays, placed under what they belong to or over it at the foot of the
  * screen, gone as the pointer leaves or the focus moves on. */
class TooltipSpec extends FxSpec {
  // the focus as the keys move it, which these are about
  override protected def keyed: Boolean = true

  private def card(left: Double, top: Double): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 100.0
    c.size.height @= 40.0
    c.position.left @= left
    c.position.top @= top
    c
  }

  private def onScreen[T](cards: Component*)(test: => T): T = {
    fx {
      Tooltip.delay @= 0.05
      Tooltip.onFocus @= true
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= cards
    }
    try test finally fx {
      Tooltip.hide()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  // the tip, the last thing on the screen: read on the FX thread
  private def tip: Option[Component] = ui.children().lastOption.filter(_ => Tooltip.shownFor.nonEmpty)

  "A tooltip" should {
    "come after the pointer rests, under what it is for, and go as it leaves" in {
      val a = fx(card(200.0, 100.0))
      onScreen(a) {
        fx(Tooltip(a, "Save"))
        fx(a.event.pointer.enter @= PointerEvent(PointerKind.Enter, 210.0, 110.0))
        fx(Tooltip.shownFor) shouldBe None
        eventually(Tooltip.shownFor.contains(a)) shouldBe true
        fx(tip.get.position.top()) shouldBe 146.0
        fx(tip.get.position.left() + tip.get.size.width() / 2.0) shouldBe 250.0 +- 0.5
        fx(a.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0))
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "sit over what it is for at the foot of the screen, and never off its edge" in {
      val a = fx(card(1860.0, 1030.0))
      onScreen(a) {
        fx(Tooltip(a, "A tip long enough to need the room"))
        fx(a.event.pointer.enter @= PointerEvent(PointerKind.Enter, 1870.0, 1040.0))
        eventually(Tooltip.shownFor.contains(a)) shouldBe true
        fx(tip.get.position.top() + tip.get.size.height()) shouldBe 1024.0 +- 0.5
        fx(tip.get.position.left() + tip.get.size.width()) should be <= 1912.0
      }
    }
    "follow the focus, showing what the app made" in {
      val (a, b) = fx((card(100.0, 100.0), card(300.0, 100.0)))
      onScreen(a, b) {
        fx(Tooltip.rich(a)(() => new TextView { content @= "Rich" }))
        fx(a.focus())
        eventually(Tooltip.shownFor.contains(a)) shouldBe true
        fx(b.focus())
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "not follow the focus where the app says not to" in {
      val a = fx(card(100.0, 100.0))
      onScreen(a) {
        fx {
          Tooltip.onFocus @= false
          Tooltip(a, "Hidden")
          a.focus()
        }
        Thread.sleep(150L)
        fx(Tooltip.shownFor) shouldBe None
      }
    }
  }
}
