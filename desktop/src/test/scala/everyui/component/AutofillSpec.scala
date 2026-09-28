package everyui.component

import everyui.component.types.{Autofill, Semantics}
import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.text.TextArea
import everyui.{FxSpec, ui}
import reactify.*

/** What a field asks for reaching its platform field, its label naming it, and a field of an app's own that types
  * every key but Tab and Escape. */
class AutofillSpec extends FxSpec {
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

  private def inputOf(c: Container): InputView = c.children().collectFirst { case i: InputView => i }.get

  "Autofill" should {
    "name every hint by its own autocomplete token" in {
      val tokens = Autofill.values.toList.map(Autofill.token)
      tokens.distinct.size shouldBe Autofill.values.length
      Autofill.token(Autofill.Email) shouldBe "email"
      Autofill.token(Autofill.Tel) shouldBe "tel"
      Autofill.token(Autofill.CurrentPassword) shouldBe "current-password"
      Autofill.token(Autofill.Off) shouldBe "off"
    }
    "reach the platform field of a text input and a text area, and follow a change" in {
      val (input, area) = fx((new TextInput("Email"), new TextArea("Notes")))
      fx {
        input.autofill @= Some(Autofill.Email)
        area.autofill @= Some(Autofill.Off)
      }
      fx(inputOf(input).autofill()) shouldBe Some(Autofill.Email)
      fx(inputOf(area).autofill()) shouldBe Some(Autofill.Off)
      fx(input.autofill @= Some(Autofill.Username))
      fx(inputOf(input).autofill()) shouldBe Some(Autofill.Username)
    }
    "name a text input's field by its label" in {
      val input = fx(new TextInput("Email"))
      fx(inputOf(input).semantics()) shouldBe Semantics(label = "Email")
      fx(input.title @= "Your email")
      fx(inputOf(input).semantics()) shouldBe Semantics(label = "Your email")
    }
  }

  "A field of an app's own that types every key but Tab and Escape" should {
    "leave the keys it types to the platform, and still move on with Tab" in {
      var heard: List[Key] = Nil
      val field = fx {
        new Container {
          focusable @= true
          size.width @= 200.0
          size.height @= 40.0
          children += new InputView
          event.key.down.attach(e => heard = heard :+ e.key)
          override def typesEvent(e: KeyEvent): Boolean = e.key != Key.Tab && e.key != Key.Escape
        }
      }
      val next = fx {
        val c = new Component(Nodes.container())
        c.focusable @= true
        c.size.width @= 100.0
        c.size.height @= 20.0
        c
      }
      onScreen(Column(8.0)(field, next)) {
        fx(field.focus())
        // the platform types them: none is taken from it
        List("j", " ", "Backspace", "ArrowLeft", "ArrowDown", "Enter", "Delete").foreach(k =>
          fx(Focus.key(k, true)) shouldBe false)
        fx(Focus.focused()) shouldBe Some(field)
        heard.size shouldBe 7
        fx(Focus.key("Tab", true))
        fx(Focus.focused()) shouldBe Some(next)
      }
    }
  }
}
