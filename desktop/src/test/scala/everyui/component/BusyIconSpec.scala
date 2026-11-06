package everyui.component

import everyui.font.MaterialIcon
import everyui.kit.Spinner
import everyui.motion.ReduceMotion
import everyui.theme.Theme
import everyui.{FxSpec, ui}

/** A busy icon's glyph breathes, or turns where it is a turning one, nothing drawn over it, and goes back to how it
  * was as it stops; where less motion is asked for it stands still, in the accent on a button; a screen reader and
  * the tooltip hear that it is busy. */
class BusyIconSpec extends FxSpec {
  override def afterAll(): Unit = {
    fx {
      ReduceMotion.on @= false
      ui.children.clear()
    }
    super.afterAll()
  }

  "A busy icon button" should {
    "turn a turning glyph, breathe another, as it is told, and stand upright again once not busy" in {
      val b = fx {
        ui.children.clear()
        val b = new IconButton(MaterialIcon.Sync, "Background work")
        ui.children += b
        b
      }
      fx(b.busy @= true)
      fx(b.icon.motion()) shouldBe List(MaterialIconView.turn)
      fx(b.semantics().label) shouldBe "Background work, busy"
      // another glyph while busy: moved its own way
      fx(b.icon.icon @= MaterialIcon.Queue)
      fx(b.icon.motion()) shouldBe List(MaterialIconView.breath)
      fx(b.icon.busyMotion @= everyui.component.types.BusyMotion.Spin)
      fx(b.icon.motion()) shouldBe List(MaterialIconView.turn)
      fx(b.busy @= false)
      fx(b.icon.motion()) shouldBe Nil
      eventually(b.icon.node.fx.getRotate == 0.0) shouldBe true
      fx(ReduceMotion.on @= true)
      fx(b.busy @= true)
      fx(b.icon.motion()) shouldBe Nil
      fx(b.icon.color()) shouldBe fx(Theme.accent())
      fx {
        b.busy @= false
        ReduceMotion.on @= false
      }
    }
    "breathe while busy, and be as it was once not" in {
      val b = fx {
        ui.children.clear()
        val b = new IconButton(MaterialIcon.Queue, "Queue")
        ui.children += b
        b
      }
      fx(b.busy @= true)
      fx(b.icon.motion()) shouldBe List(MaterialIconView.breath)
      fx(b.children().length) shouldBe 1
      fx(b.semantics().label) shouldBe "Queue, busy"
      fx(b.tooltip()) shouldBe "Queue, busy"
      fx(b.busy @= false)
      fx(b.icon.motion()) shouldBe Nil
      fx(b.icon.opacity()) shouldBe 1.0
      fx(b.semantics().label) shouldBe "Queue"
      fx(b.tooltip()) shouldBe "Queue"
    }
    "stand still in the accent where less motion is asked for, and breathe again where it is not" in {
      val b = fx {
        ReduceMotion.on @= true
        val b = new IconButton(MaterialIcon.Queue, "Queue")
        ui.children += b
        b.busy @= true
        b
      }
      fx(b.icon.motion()) shouldBe Nil
      fx(b.icon.opacity()) shouldBe 1.0
      fx(b.icon.color()) shouldBe fx(Theme.accent())
      fx(ReduceMotion.on @= false)
      fx(b.icon.motion()) shouldBe List(MaterialIconView.breath)
      fx(b.icon.opacity()) shouldBe 1.0
      fx(b.icon.color()) should not be fx(Theme.accent())
      fx(b.busy @= false)
    }
  }

  "A glyph with a motion of its own" should {
    "have it back once it is no longer busy" in {
      val s = fx(new Spinner())
      val turning = fx(s.motion())
      fx(s.busy @= true)
      // its glyph a turning one, it turns as a busy glyph does
      fx(s.motion()) shouldBe List(MaterialIconView.turn)
      fx(s.busy @= false)
      fx(s.motion()) shouldBe turning
    }
    "stand still at half strength where less motion is asked for, and be whole again once not busy" in {
      val g = fx {
        ReduceMotion.on @= true
        val g = new MaterialIconView(MaterialIcon.Queue)
        g.busy @= true
        g
      }
      fx((g.motion(), g.opacity())) shouldBe (Nil, MaterialIconView.still)
      fx(g.busy @= false)
      fx(g.opacity()) shouldBe 1.0
      fx(ReduceMotion.on @= false)
    }
  }
}
