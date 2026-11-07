package everyui.component

import everyui.component.types.{Autofill, TextDirection}
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import javafx.geometry.NodeOrientation
import javafx.scene.control.TextInputControl

/** A field's words run the app's way, except a literal one's, and one asking for an address, a sign-in or a code,
  * which run left to right in a right-to-left language too, so "*.scala" reads as typed; while empty every field reads
  * as the app does, for its placeholder. */
class InputDirectionSpec extends FxSpec {
  private def turned(field: InputView): NodeOrientation =
    field.node.fx.asInstanceOf[TextInputControl].getNodeOrientation

  private def inArabic[T](test: => T): T = {
    fx(Direction.set(true))
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
    }
  }

  "A field in a right-to-left language" should {
    "run a literal field's words left to right, and read as the app does while empty" in inArabic {
      val field = fx(new InputView {
        literal @= true
        placeholder @= "قناع الملف"
      })
      fx(ui.children += field)
      fx(field.runs()) shouldBe TextDirection.Inherit
      fx(turned(field)) shouldBe NodeOrientation.RIGHT_TO_LEFT
      fx(field.text @= "*.scala")
      fx(field.runs()) shouldBe TextDirection.LeftToRight
      fx(turned(field)) shouldBe NodeOrientation.LEFT_TO_RIGHT
      fx(field.text @= "")
      fx(turned(field)) shouldBe NodeOrientation.RIGHT_TO_LEFT
    }
    "run a plain field's words the app's way, whatever their script" in inArabic {
      val field = fx(new InputView)
      fx(ui.children += field)
      fx(field.text @= "*.scala")
      fx(field.runs()) shouldBe TextDirection.Inherit
      fx(turned(field)) shouldBe NodeOrientation.RIGHT_TO_LEFT
      fx(Direction.set(false))
      fx(turned(field)) shouldBe NodeOrientation.LEFT_TO_RIGHT
    }
    "run an email address, a web address, a sign-in and a code left to right" in inArabic {
      List(Autofill.Email, Autofill.Url, Autofill.Username, Autofill.CurrentPassword, Autofill.OneTimeCode,
        Autofill.Tel).foreach { asks =>
        val field = fx(new InputView)
        fx(field.autofill @= Some(asks))
        fx(field.text @= "name@host.com")
        fx(turned(field)) shouldBe NodeOrientation.LEFT_TO_RIGHT
      }
      val name = fx(new InputView)
      fx(name.autofill @= Some(Autofill.Name))
      fx(name.text @= "Matt")
      fx(turned(name)) shouldBe NodeOrientation.RIGHT_TO_LEFT
    }
    "run a field of digits left to right, and from a text input too" in inArabic {
      val field = fx(new InputView)
      fx(field.numeric @= true)
      fx(field.text @= "1234")
      fx(turned(field)) shouldBe NodeOrientation.LEFT_TO_RIGHT
      fx(field.numeric @= false)
      fx(turned(field)) shouldBe NodeOrientation.RIGHT_TO_LEFT
      val pin = fx(new TextInput("PIN", secure = true))
      fx(pin.numeric @= true)
      fx(ui.children += pin)
      fx(pin.value @= "1234")
      val inner = fx(pin.children().collectFirst { case i: InputView => i }.get)
      fx(inner.numeric()) shouldBe true
      fx(turned(inner)) shouldBe NodeOrientation.LEFT_TO_RIGHT
    }
    "run words by their own first letter, or the way the app says, where it says" in inArabic {
      val field = fx(new InputView)
      fx(field.direction @= TextDirection.Auto)
      fx(field.text @= "hello")
      fx(turned(field)) shouldBe NodeOrientation.LEFT_TO_RIGHT
      fx(field.text @= "مرحبا")
      fx(turned(field)) shouldBe NodeOrientation.RIGHT_TO_LEFT
      val literal = fx(new InputView)
      fx(literal.literal @= true)
      fx(literal.direction @= TextDirection.RightToLeft)
      fx(literal.text @= "abc")
      fx(turned(literal)) shouldBe NodeOrientation.RIGHT_TO_LEFT
    }
    "pass the way to the field from a search field, a text input and a box of lines" in inArabic {
      val search = fx(new everyui.kit.SearchField)
      fx(search.literal @= true)
      fx(search.value @= "*.scala")
      val inner = fx(search.children().collectFirst { case i: InputView => i }.get)
      fx(turned(inner)) shouldBe NodeOrientation.LEFT_TO_RIGHT
      val input = fx(new TextInput("Address"))
      fx(input.direction @= TextDirection.LeftToRight)
      fx(input.value @= "a")
      val innerInput = fx(input.children().collectFirst { case i: InputView => i }.get)
      fx(innerInput.runs()) shouldBe TextDirection.LeftToRight
      val area = fx(new everyui.text.TextArea)
      fx(area.literal @= true)
      fx(area.value @= "val x = 1")
      val innerArea = fx(area.children().collectFirst { case i: InputView => i }.get)
      fx(turned(innerArea)) shouldBe NodeOrientation.LEFT_TO_RIGHT
    }
  }
}
