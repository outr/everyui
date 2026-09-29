package everyui.slider

import everyui.component.{Component, Switch}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** Sliders, range sliders and switches on the desktop's real nodes: the keys and the pointer setting them, a right-to-
  * left language mirroring them, their thumbs kept apart, what a screen reader hears, and nothing done while disabled. */
class SliderSpec extends FxSpec {
  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      c.position.left @= 100.0
      c.position.top @= 50.0
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
      Direction.set(false)
    }
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def pointer(c: Component, kind: PointerKind, x: Double, y: Double): Unit = fx {
    val e = PointerEvent(kind, c.absoluteLeft + x, c.absoluteTop + y)
    kind match {
      case PointerKind.Down => c.event.pointer.down @= e
      case PointerKind.Move => c.event.pointer.move @= e
      case _ => c.event.pointer.up @= e
    }
  }

  private def focusedOn(c: Component): Boolean = fx(Focus.focused().exists(_ eq c))

  "A slider" should {
    "move a step an arrow, a tenth a page, to its ends with Home and End, and tell each change's end" in {
      val value = Var(40.0)
      val slider = fx(new Slider(value, 0.0, 100.0, step = 1.0, label = "Volume", format = v => SliderScale.text(v) + "%"))
      val ends = fx(Var(List.empty[Double]))
      fx(slider.onChangeEnd.attach(v => ends @= ends() :+ v))
      onScreen(slider) {
        fx(slider.focus())
        press(KeyCode.RIGHT)
        press(KeyCode.RIGHT)
        fx(value()) shouldBe 42.0
        press(KeyCode.LEFT)
        fx(value()) shouldBe 41.0
        press(KeyCode.PAGE_UP)
        fx(value()) shouldBe 51.0
        press(KeyCode.END)
        fx(value()) shouldBe 100.0
        press(KeyCode.HOME)
        fx(value()) shouldBe 0.0
        fx(ends()) shouldBe List(41.0, 42.0, 41.0, 51.0, 100.0, 0.0)
        // Up and Down are the focus layer's: nothing moves, and the slider is not left somewhere it cannot be seen
        press(KeyCode.UP)
        fx(value()) shouldBe 0.0
        fx(slider.semantics().role) shouldBe "slider"
        fx(slider.semantics().range.map(r => (r.now, r.min, r.max, r.text))) shouldBe Some((0.0, 0.0, 100.0, "0%"))
        fx(slider.semantics().label) shouldBe "Volume"
      }
    }
    "go where it is pressed, follow a drag past its end, and tell the end once it lets go" in {
      val value = Var(0.0)
      val slider = fx(new Slider(value, 0.0, 100.0, step = 5.0))
      val ends = fx(Var(List.empty[Double]))
      fx(slider.onChangeEnd.attach(v => ends @= ends() :+ v))
      onScreen(slider) {
        // the rail runs from 20 to 220 along the 240 of the box
        pointer(slider, PointerKind.Down, 20.0 + 51.0, 20.0)
        fx(value()) shouldBe 25.0
        focusedOn(slider) shouldBe true
        pointer(slider, PointerKind.Move, 20.0 + 139.0, 5.0)
        fx(value()) shouldBe 70.0
        fx(ends()) shouldBe Nil
        pointer(slider, PointerKind.Move, 900.0, 300.0)
        pointer(slider, PointerKind.Up, 900.0, 300.0)
        fx(value()) shouldBe 100.0
        fx(ends()) shouldBe List(100.0)
      }
    }
    "run from the right in a right-to-left language, its arrows with it" in {
      val value = Var(50.0)
      val slider = fx(new Slider(value, 0.0, 100.0, step = 1.0))
      onScreen(slider) {
        fx(Direction.set(true))
        pointer(slider, PointerKind.Down, 20.0 + 50.0, 20.0)
        pointer(slider, PointerKind.Up, 20.0 + 50.0, 20.0)
        fx(value()) shouldBe 75.0
        press(KeyCode.RIGHT)
        fx(value()) shouldBe 74.0
      }
    }
    "run up when vertical, with Up and Down" in {
      val value = Var(50.0)
      val slider = fx(new Slider(value, 0.0, 10.0, step = 1.0, vertical = true, length = 220.0))
      onScreen(slider) {
        fx((slider.size.width(), slider.size.height())) shouldBe (40.0, 220.0)
        pointer(slider, PointerKind.Down, 20.0, 20.0 + 18.0)
        pointer(slider, PointerKind.Up, 20.0, 20.0 + 18.0)
        fx(value()) shouldBe 9.0
        press(KeyCode.DOWN)
        fx(value()) shouldBe 8.0
        press(KeyCode.RIGHT)
        fx(value()) shouldBe 8.0
      }
    }
    "take neither the focus nor a press, nor a key, while disabled" in {
      val value = Var(30.0)
      val slider = fx(new Slider(value, 0.0, 100.0, step = 1.0))
      fx(slider.disabled @= true)
      onScreen(slider) {
        fx(slider.focusable()) shouldBe false
        pointer(slider, PointerKind.Down, 200.0, 20.0)
        pointer(slider, PointerKind.Up, 200.0, 20.0)
        fx(value()) shouldBe 30.0
        fx(slider.disabled @= false)
        fx(slider.focusable()) shouldBe true
      }
    }
  }

  "A range slider" should {
    "take the nearer thumb, and keep it from crossing the other by the gap" in {
      val (low, high) = (Var(20.0), Var(80.0))
      val range = fx(new RangeSlider(low, high, 0.0, 100.0, step = 5.0, minGap = 10.0, label = "Price"))
      val ends = fx(Var(List.empty[(Double, Double)]))
      fx(range.onChangeEnd.attach(v => ends @= ends() :+ v))
      onScreen(range) {
        pointer(range, PointerKind.Down, 20.0 + 60.0, 20.0)   // 30: nearer the start
        fx(low()) shouldBe 30.0
        focusedOn(range.startThumb) shouldBe true
        pointer(range, PointerKind.Move, 20.0 + 190.0, 20.0)  // 95: past the end
        pointer(range, PointerKind.Up, 20.0 + 190.0, 20.0)
        fx((low(), high())) shouldBe (70.0, 80.0)
        fx(ends()) shouldBe List((70.0, 80.0))
        pointer(range, PointerKind.Down, 20.0 + 196.0, 20.0)  // 98: the end's
        pointer(range, PointerKind.Up, 20.0 + 196.0, 20.0)
        fx(high()) shouldBe 100.0
        focusedOn(range.endThumb) shouldBe true
      }
    }
    "move each thumb from the keys, Tab and OK going between them" in {
      val (low, high) = (Var(20.0), Var(80.0))
      val range = fx(new RangeSlider(low, high, 0.0, 100.0, step = 1.0, minGap = 5.0, label = "Price"))
      onScreen(range) {
        fx(range.startThumb.focus())
        press(KeyCode.RIGHT)
        fx(low()) shouldBe 21.0
        press(KeyCode.END)
        fx(low()) shouldBe 75.0
        press(KeyCode.TAB)
        focusedOn(range.endThumb) shouldBe true
        press(KeyCode.HOME)
        fx(high()) shouldBe 80.0
        press(KeyCode.PAGE_UP)
        fx(high()) shouldBe 90.0
        press(KeyCode.ENTER)
        focusedOn(range.startThumb) shouldBe true
        press(KeyCode.HOME)
        fx(low()) shouldBe 0.0
        fx(range.startThumb.semantics().range.map(r => (r.now, r.min, r.max))) shouldBe Some((0.0, 0.0, 90.0))
        fx(range.endThumb.semantics().range.map(r => (r.now, r.min, r.max))) shouldBe Some((90.0, 0.0, 100.0))
      }
    }
    "mirror in a right-to-left language" in {
      val (low, high) = (Var(20.0), Var(80.0))
      val range = fx(new RangeSlider(low, high, 0.0, 100.0, step = 5.0))
      onScreen(range) {
        fx(Direction.set(true))
        // the start sits at the right: 20 is 160 in from the rail's left
        fx(range.startThumb.position.left() + 20.0) shouldBe (20.0 + 160.0)
        pointer(range, PointerKind.Down, 20.0 + 150.0, 20.0)
        pointer(range, PointerKind.Up, 20.0 + 150.0, 20.0)
        fx((low(), high())) shouldBe (25.0, 80.0)
      }
    }
  }

  "A switch" should {
    "flip with a tap, OK and Space, and say so to a screen reader" in {
      val sw = fx(new Switch("Wi-Fi"))
      onScreen(sw) {
        fx(sw.on()) shouldBe false
        fx(sw.semantics().checked) shouldBe Some(false)
        pointer(sw, PointerKind.Down, 10.0, 10.0)
        pointer(sw, PointerKind.Up, 10.0, 10.0)
        fx(sw.on()) shouldBe true
        fx(sw.semantics()) shouldBe everyui.component.types.Semantics.switch("Wi-Fi", true)
        focusedOn(sw) shouldBe true
        press(KeyCode.ENTER)
        fx(sw.on()) shouldBe false
        press(KeyCode.SPACE)
        fx(sw.on()) shouldBe true
      }
    }
    "land on the side a drag lets go nearer" in {
      val sw = fx(new Switch("Wi-Fi"))
      onScreen(sw) {
        val trackLeft = fx(sw.size.width() - Switch.pad - Switch.trackWidth)
        pointer(sw, PointerKind.Down, trackLeft + 16.0, 20.0)
        pointer(sw, PointerKind.Move, trackLeft + 30.0, 20.0)
        pointer(sw, PointerKind.Up, trackLeft + 30.0, 20.0)
        fx(sw.on()) shouldBe true
        pointer(sw, PointerKind.Down, trackLeft + 36.0, 20.0)
        pointer(sw, PointerKind.Move, trackLeft + 30.0, 20.0)
        pointer(sw, PointerKind.Up, trackLeft + 30.0, 20.0)
        fx(sw.on()) shouldBe true
        pointer(sw, PointerKind.Down, trackLeft + 36.0, 20.0)
        pointer(sw, PointerKind.Move, trackLeft + 10.0, 20.0)
        pointer(sw, PointerKind.Up, trackLeft + 10.0, 20.0)
        fx(sw.on()) shouldBe false
      }
    }
    "do nothing while disabled" in {
      val sw = fx(new Switch("Wi-Fi", Var(true)))
      fx(sw.disabled @= true)
      onScreen(sw) {
        fx(sw.focusable()) shouldBe false
        pointer(sw, PointerKind.Down, 10.0, 10.0)
        pointer(sw, PointerKind.Up, 10.0, 10.0)
        fx(sw.on()) shouldBe true
      }
    }
  }
}
