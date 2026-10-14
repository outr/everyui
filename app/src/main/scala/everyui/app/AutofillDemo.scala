package everyui.app

import everyui.chat.Composer
import everyui.component.{Button, Column, TextInput}
import everyui.component.types.Autofill
import everyui.form.{Form, FormKind}
import everyui.kit.SearchField
import everyui.render.Platform
import everyui.ui

/** A sign-in and a contact form, as autofill and password managers see them: each field says what it asks for, each
  * form is one, and what is typed or filled is logged as it reaches the field's value, so a page check (tools/check.sh)
  * can fill them as a password manager does and see the values arrive:
  *
  *   [ui] autofill email=ann@example.com
  *   [ui] autofill signed in as ann@example.com
  *
  * Beside them, fields that say nothing (a search box and a chat's composer), which "Unhinted off" holds out of autofill
  * with every other such field (`Autofill.fallback`), as `fallback=off` does from the start; "Remove sign-in" takes the
  * sign-in away unsent. `step=1` makes the sign-in a password-only step for an account asked a step before
  * (`Form.hiddenUsername`).
  */
object AutofillDemo {
  def run(): Unit = {
    if (Platform.launchArg("fallback") == "off") Autofill.fallback @= Some(Autofill.Off)
    val step = Platform.launchArg("step") == "1"
    val signIn = new Form(FormKind.SignIn)
    val email = field("Email", "email", Autofill.Username)
    val password = new TextInput("Password", secure = true)
    password.autofill @= Some(Autofill.CurrentPassword)
    password.value.attach(v => Platform.log("autofill password of " + v.length.toString))
    password.submitted.attach(_ => signIn.submit())
    if (step) signIn.hiddenUsername @= "ann@example.com"
    signIn.submitted.attach { _ =>
      Platform.log("autofill signed in as " + (if (step) signIn.hiddenUsername() else email.value()))
      signIn.succeeded()
    }
    signIn.children += (if (step) Column(12.0)(password, new Button("Sign in", () => signIn.submit()))
      else Column(12.0)(email, password, new Button("Sign in", () => signIn.submit())))

    val contact = new Form(FormKind.Contact)
    contact.children += Column(12.0)(field("Name", "name", Autofill.Name), field("Email", "contact-email", Autofill.Email),
      field("Phone", "tel", Autofill.Tel), field("Notes", "notes", Autofill.Off))
    contact.position.left @= 400.0

    val search = new SearchField
    val composer = new Composer(draft => Platform.log("autofill sent " + draft.text), width = 640.0)
    val off = new Button("Unhinted off", () => Autofill.fallback @= (if (Autofill.fallback().isEmpty) Some(Autofill.Off) else None))
    val remove = new Button("Remove sign-in", () => if (ui.children().contains(signIn)) {
      ui.children -= signIn
      signIn.dispose()
    })
    val unhinted = Column(12.0)(search, composer, off, remove)
    unhinted.position.left @= 40.0
    unhinted.position.top @= 420.0

    signIn.position.left @= 40.0
    signIn.position.top @= 40.0
    contact.position.top @= 40.0
    ui.children ++= List(signIn, contact, unhinted)
  }

  private def field(label: String, name: String, hint: Autofill): TextInput = {
    val input = new TextInput(label)
    input.autofill @= Some(hint)
    input.fieldName @= name
    input.value.attach(v => Platform.log("autofill " + name + "=" + v))
    input
  }
}
