package everyui.text

import everyui.component.{InputView, TextInput}
import everyui.{FxSpec, ui}
import javafx.event.Event
import javafx.scene.control.TextInputControl
import javafx.scene.input.{Clipboard, ClipboardContent, KeyCode, KeyEvent}

/** A read-only field (`InputView.readOnly`) shows its words, which can be chosen and copied, but typing, deleting and
  * pasting change nothing, and it looks as it did: it is not disabled. Keys are sent to the field's own node. The
  * clipboard is the system's, so this runs where it is a display of its own (Xvfb). */
class ReadOnlySpec extends FxSpec {
  private def control(v: InputView): TextInputControl = v.node.fx.asInstanceOf[TextInputControl]

  private def key(target: TextInputControl, code: KeyCode, control: Boolean = false): Unit = {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, control, false, false))
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, control, false, false))
  }

  private def typed(target: TextInputControl, character: String): Unit =
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, character, "", KeyCode.UNDEFINED, false, false, false, false))

  private def clipboard(text: String): Unit = {
    val content = new ClipboardContent
    content.putString(text)
    Clipboard.getSystemClipboard.setContent(content)
  }

  "A read-only field" should {
    "be chosen and copied, but take no typing, deleting or paste" in {
      val field = fx(new InputView)
      fx {
        field.text @= "KEY-1234"
        field.readOnly @= true
        ui.children += field
        control(field).applyCss()
      }
      try {
        val tf = fx(control(field))
        fx(tf.isEditable) shouldBe false
        fx(tf.isDisabled) shouldBe false
        fx {
          tf.requestFocus()
          tf.selectAll()
          tf.getSelectedText
        } shouldBe "KEY-1234"
        fx {
          clipboard("before")
          key(tf, KeyCode.C, control = true)
          Clipboard.getSystemClipboard.getString
        } shouldBe "KEY-1234"
        fx {
          typed(tf, "x")
          key(tf, KeyCode.BACK_SPACE)
          key(tf, KeyCode.DELETE)
          clipboard("PASTED")
          key(tf, KeyCode.V, control = true)
          key(tf, KeyCode.X, control = true)
          tf.getText
        } shouldBe "KEY-1234"
        fx(field.text()) shouldBe "KEY-1234"
        // editable again, the same keys edit it
        fx {
          field.readOnly @= false
          tf.end()
          typed(tf, "x")
          field.text()
        } shouldBe "KEY-1234x"
      }
      finally fx(ui.children.clear())
    }

    "stay out of use while disabled, whichever is lifted first" in {
      val field = fx(new InputView)
      fx(ui.children += field)
      try {
        val tf = fx(control(field))
        fx {
          field.disabled @= true
          field.readOnly @= true
          field.readOnly @= false
          tf.isEditable
        } shouldBe false
        fx {
          field.readOnly @= true
          field.disabled @= false
          tf.isEditable
        } shouldBe false
        fx {
          field.readOnly @= false
          tf.isEditable
        } shouldBe true
      }
      finally fx(ui.children.clear())
    }

    "be what a TextInput and a TextArea are, read-only" in {
      val input = fx(new TextInput("Share link"))
      val area = fx(new TextArea)
      fx {
        input.value @= "https://example.com/s/1"
        input.readOnly @= true
        area.value @= "a note"
        area.readOnly @= true
        ui.children ++= List(input, area)
      }
      try {
        val fields = fx(List(input, area).flatMap(c => c.children().collect { case v: InputView => control(v) }))
        fields.length shouldBe 2
        fx(fields.map(_.isEditable)) shouldBe List(false, false)
        fx {
          fields.foreach { f =>
            f.applyCss()
            typed(f, "x")
          }
          (input.value(), area.value())
        } shouldBe ("https://example.com/s/1", "a note")
        fx {
          input.readOnly @= false
          area.readOnly @= false
          fields.map(_.isEditable)
        } shouldBe List(true, true)
      }
      finally fx(ui.children.clear())
    }
  }
}
