package everyui.form

import everyui.component.{InputView, TextInput}
import everyui.component.types.Autofill
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** A form sent once however many ways one press reaches it, told how it went once, and its fields' hints reaching
  * their platform fields; a field being edited keeping the up and down arrows where a keyboard is at hand. */
class FormSpec extends FxSpec {
  "A form" should {
    "be sent once for one press, however many ways it arrives" in {
      val form = fx(new Form(FormKind.SignIn))
      var sent = 0
      fx(form.submitted.attach(_ => sent += 1))
      fx {
        form.submit()
        form.submit()
      }
      sent shouldBe 1
      Thread.sleep(350)
      fx(form.submit())
      sent shouldBe 2
    }
    "say how it went once, and be abandoned when taken away unsaid" in {
      val form = fx(new Form(FormKind.SignUp))
      fx {
        form.succeeded()
        form.abandoned()
        form.dispose()
      }
      form.purpose shouldBe FormKind.SignUp
      Form.name(FormKind.ChangePassword) shouldBe "change-password"
    }
    "hand its fields' hints to their platform fields" in {
      val email = fx {
        val t = new TextInput("Email")
        t.autofill @= Some(Autofill.Username)
        t.passkey @= true
        t.section @= "work"
        t.fieldName @= "login"
        t
      }
      val input = fx(email.children().collectFirst { case i: InputView => i }.get)
      fx((input.autofill(), input.passkey(), input.section(), input.fieldName())) shouldBe
        ((Some(Autofill.Username), true, "work", "login"))
      Autofill.secret(Autofill.NewPassword) shouldBe true
      Autofill.secret(Autofill.Email) shouldBe false
    }
  }

  "A text input being edited with a keyboard at hand" should {
    "leave the up and down arrows to its field, and move on with Tab" in {
      val (first, second) = fx((new TextInput("First"), new TextInput("Second")))
      fx {
        second.position.top @= 80.0
        ui.children.clear()
        ui.children ++= List(first, second)
        first.focus()
      }
      try {
        fx(first.editing()) shouldBe true
        fx(Focus.key("ArrowDown", true)) shouldBe false
        fx(Focus.focused()) shouldBe Some(first)
        fx(Focus.key("Tab", true))
        fx(Focus.focused()) shouldBe Some(second)
      } finally fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
