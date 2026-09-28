package everyui.app

import everyui.component.{Button, Column, TextInput}
import everyui.component.types.Autofill
import everyui.form.{Form, FormKind}
import everyui.render.Platform
import everyui.ui

/** A sign-in and a contact form, as autofill and password managers see them: each field says what it asks for, each
  * form is one, and what is typed or filled is logged as it reaches the field's value, so a page check (tools/check.sh)
  * can fill them as a password manager does and see the values arrive:
  *
  *   [ui] autofill email=ann@example.com
  *   [ui] autofill signed in as ann@example.com
  */
object AutofillDemo {
  def run(): Unit = {
    val signIn = new Form(FormKind.SignIn)
    val email = field("Email", "email", Autofill.Username)
    val password = new TextInput("Password", secure = true)
    password.autofill @= Some(Autofill.CurrentPassword)
    password.value.attach(v => Platform.log("autofill password of " + v.length.toString))
    password.submitted.attach(_ => signIn.submit())
    signIn.submitted.attach { _ =>
      Platform.log("autofill signed in as " + email.value())
      signIn.succeeded()
    }
    signIn.children += Column(12.0)(email, password, new Button("Sign in", () => signIn.submit()))

    val contact = new Form(FormKind.Contact)
    contact.children += Column(12.0)(field("Name", "name", Autofill.Name), field("Email", "contact-email", Autofill.Email),
      field("Phone", "tel", Autofill.Tel), field("Notes", "notes", Autofill.Off))
    contact.position.left @= 400.0

    signIn.position.left @= 40.0
    signIn.position.top @= 40.0
    contact.position.top @= 40.0
    ui.children ++= List(signIn, contact)
  }

  private def field(label: String, name: String, hint: Autofill): TextInput = {
    val input = new TextInput(label)
    input.autofill @= Some(hint)
    input.fieldName @= name
    input.value.attach(v => Platform.log("autofill " + name + "=" + v))
    input
  }
}
