package everyui.text

import everyui.component.InputView
import everyui.{FxSpec, ui}
import javafx.event.Event
import javafx.scene.control.TextInputControl
import javafx.scene.control.skin.TextInputControlSkin
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}

/** A field's caret blinks only while the field has the focus. JavaFX's skin started its blinking, a timeline that never
  * ends, as the caret moved and as a press was let go, the focus or not, so a field with none (here, in no window)
  * blinked unseen and kept JavaFX pulsing until it was collected, which held MotionSpec's and BusyRingSpec's idle
  * checks off whenever a field of the text specs before them had not been yet. */
class CaretBlinkSpec extends FxSpec {
  private def control(v: InputView): TextInputControl = v.node.fx.asInstanceOf[TextInputControl]

  // whether the skin's caret timeline runs (its own check, kept for tests)
  private def blinking(c: TextInputControl): Boolean = {
    val check = classOf[TextInputControlSkin[?]].getDeclaredMethod("isCaretBlinking")
    check.setAccessible(true)
    check.invoke(c.getSkin).asInstanceOf[Boolean]
  }

  private def typed(target: TextInputControl, character: String): Unit =
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, character, "", KeyCode.UNDEFINED, false, false, false,
      false))

  private def mouse(target: TextInputControl, kind: javafx.event.EventType[MouseEvent]): Unit =
    Event.fireEvent(target, new MouseEvent(kind, 4.0, 4.0, 4.0, 4.0, MouseButton.PRIMARY, 1, false, false, false,
      false, true, false, false, false, false, false, null))

  "A field's caret" should {
    "not blink in a field with no focus, its words set, typed, its caret moved or a press let go" in {
      val fields = fx(List(new InputView, new InputView(secure = true), new InputView(multiline = true)))
      fx {
        fields.foreach { f =>
          ui.children += f
          control(f).applyCss()
        }
      }
      try {
        fields.foreach { f =>
          val c = fx(control(f))
          fx(c.isFocused) shouldBe false
          fx {
            f.text @= "words"
            c.end()
            c.positionCaret(2)
            typed(c, "x")
            mouse(c, MouseEvent.MOUSE_PRESSED)
            mouse(c, MouseEvent.MOUSE_RELEASED)
            blinking(c)
          } shouldBe false
        }
      }
      finally fx(ui.children.clear())
    }

    "blink while its field has the focus, and stop as the field loses it" in {
      onStage()
      val field = fx(new InputView)
      val other = fx(new InputView)
      fx {
        ui.children += field
        ui.children += other
        control(field).applyCss()
        control(other).applyCss()
      }
      try {
        val c = fx(control(field))
        fx(c.requestFocus())
        // a window in front under Xvfb, which has no window manager to hold one back
        eventually(c.isFocused) shouldBe true
        fx(blinking(c)) shouldBe true
        fx {
          c.positionCaret(0)
          blinking(c)
        } shouldBe true
        fx(control(other).requestFocus())
        eventually(!c.isFocused) shouldBe true
        fx(blinking(c)) shouldBe false
        fx {
          c.positionCaret(1)
          blinking(c)
        } shouldBe false
      }
      finally {
        fx(ui.children.clear())
        offStage()
      }
    }
  }
}
