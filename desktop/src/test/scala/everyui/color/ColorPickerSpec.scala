package everyui.color

import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.{Color, FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A picker on the desktop's real nodes: its fields, area and sliders in step with its value both ways, the keys and
  * a drag moving them, a swatch choosing, and alpha allowed or not. */
class ColorPickerSpec extends FxSpec {
  private def onScreen[T](picker: ColorPicker)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      picker.position.left @= 100.0
      picker.position.top @= 50.0
      ui.children += picker
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def press(code: KeyCode, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def fields(picker: ColorPicker): List[everyui.component.TextInput] =
    everyui.focus.Focus.candidates(picker).collect { case t: everyui.component.TextInput => t }.toList

  private def find[T](picker: ColorPicker, f: PartialFunction[everyui.component.Component, T]): List[T] =
    Focus.candidates(picker).collect(f).toList

  "A picker" should {
    "show its value in its fields, and take a colour typed into them" in {
      val value = Var(Color.fromHex("1e88e5"))
      val picker = fx(new ColorPicker(value))
      onScreen(picker) {
        val List(hex, r, g, b) = fx(fields(picker))
        fx((hex.value(), r.value(), g.value(), b.value())) shouldBe ("#1e88e5", "30", "136", "229")
        fx(hex.value @= "#ff8800")
        fx(value()) shouldBe Color.fromHex("ff8800")
        fx((r.value(), g.value(), b.value())) shouldBe ("255", "136", "0")
        fx(hex.value @= "#ff88a")  // not a colour yet: nothing changes
        fx(value()) shouldBe Color.fromHex("ff8800")
        fx(g.value @= "0")
        fx(value()) shouldBe Color.fromHex("ff0000")
        fx(hex.value()) shouldBe "#ff0000"
        fx(g.value @= "300")       // out of range: nothing changes
        fx(value()) shouldBe Color.fromHex("ff0000")
        fx(value @= Color.fromHex("00ff00"))
        fx((hex.value(), r.value(), g.value())) shouldBe ("#00ff00", "0", "255")
      }
    }
    "keep a colour opaque unless alpha is allowed" in {
      val value = Var(Color.fromHex("1e88e5"))
      val picker = fx(new ColorPicker(value))
      onScreen(picker) {
        val hex = fx(fields(picker).head)
        fx(hex.value @= "#11223380")
        fx(value()) shouldBe Color.fromHex("112233")
      }
      val clear = Var(Color.fromHex("1e88e5"))
      val withAlpha = fx(new ColorPicker(clear, alpha = true))
      onScreen(withAlpha) {
        val hex = fx(fields(withAlpha).head)
        fx(hex.value @= "#11223380")
        fx(clear()) shouldBe new Color(0x11, 0x22, 0x33, 0x80)
        val alpha = fx(find(withAlpha, { case s: ColorSlider if s.kind.contains("Opacity") => s }).head)
        fx(alpha.focus())
        press(KeyCode.END)
        fx(clear().alphaInt) shouldBe 255
      }
    }
    "move the hue by a degree an arrow, ten with Shift, and hold it through grey" in {
      val value = Var(Color.fromHex("ff0000"))
      val picker = fx(new ColorPicker(value))
      onScreen(picker) {
        val hue = fx(find(picker, { case s: ColorSlider if s.kind.contains("Hue") => s }).head)
        fx(hue.focus())
        press(KeyCode.RIGHT, shift = true)
        fx(math.round(picker.hsv().hue * 360.0)) shouldBe 10L
        press(KeyCode.RIGHT)
        fx(math.round(picker.hsv().hue * 360.0)) shouldBe 11L
        fx(Focus.focused().exists(_ eq hue)) shouldBe true
        fx(value @= Color.fromHex("808080"))
        fx(math.round(picker.hsv().hue * 360.0)) shouldBe 11L
        fx(hue.semantics().label) shouldBe "Hue 11°"
      }
    }
    "move the area's thumb with the arrows, and set it where it is pressed and dragged" in {
      val value = Var(Color.fromHex("ff0000"))
      val picker = fx(new ColorPicker(value))
      onScreen(picker) {
        val area = fx(find(picker, { case a: ColorArea => a }).head)
        fx(area.focus())
        press(KeyCode.LEFT, shift = true)
        press(KeyCode.DOWN)
        fx((math.round(picker.hsv().saturation * 100), math.round(picker.hsv().value * 100))) shouldBe (90L, 99L)
        fx(Focus.focused().exists(_ eq area)) shouldBe true
        val (left, top, w, h) = fx((area.absoluteLeft, area.absoluteTop, area.size.width(), area.size.height()))
        fx(area.event.pointer.down @= PointerEvent(PointerKind.Down, left + w / 4.0, top + h / 2.0))
        fx((math.round(picker.hsv().saturation * 100), math.round(picker.hsv().value * 100))) shouldBe (25L, 50L)
        fx(area.event.pointer.move @= PointerEvent(PointerKind.Move, left + w * 2.0, top - 50.0))
        fx(area.event.pointer.up @= PointerEvent(PointerKind.Up, left + w * 2.0, top - 50.0))
        fx(value()) shouldBe Color.fromHex("ff0000")
        fx(area.semantics().label) shouldBe "Saturation 100%, Brightness 100%"
      }
    }
    "choose a preset, and show the recent colours once one is remembered" in {
      val value = Var(Color.White)
      val recent = Var(List.empty[Color])
      val picker = fx(new ColorPicker(value, presets = List(Color.fromHex("f44336"), Color.fromHex("2196f3")), recent = recent))
      onScreen(picker) {
        val swatches = fx(find(picker, { case s: ColorSwatch => s }))
        swatches.size shouldBe 2
        fx(swatches(1).focus())
        press(KeyCode.ENTER)
        fx(value()) shouldBe Color.fromHex("2196f3")
        fx(picker.remember())
        fx(recent()) shouldBe List(Color.fromHex("2196f3"))
        fx(find(picker, { case s: ColorSwatch => s }).size) shouldBe 3
      }
    }
  }
}
