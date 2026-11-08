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
  * Beside them, fields that say nothing (a search box, a chat's composer, and two of digits on a phone's number pad: a
  * count of seats and a hidden PIN, `numeric`), which "Unhinted off" holds out of autofill with every other such field
  * (`Autofill.fallback`), as `fallback=off` does from the start; "Remove sign-in" takes the sign-in away unsent.
  * `later=1` puts the sign-in up three seconds after the rest, as a sign-in card shown in a chat is, after the first
  * autofill request; "New sign-in" puts a new one up.
  * `step=1` makes the sign-in a password-only step for an account asked a step before (`Form.hiddenUsername`).
  */
object AutofillDemo {
  def run(): Unit = {
    if (Platform.launchArg("fallback") == "off") Autofill.fallback @= Some(Autofill.Off)
    val step = Platform.launchArg("step") == "1"
    def signInForm(): Form = {
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
      signIn.position.left @= 40.0
      signIn.position.top @= 40.0
      signIn
    }
    var signIn = signInForm()

    val contact = new Form(FormKind.Contact)
    contact.children += Column(12.0)(field("Name", "name", Autofill.Name), field("Email", "contact-email", Autofill.Email),
      field("Phone", "tel", Autofill.Tel), field("Notes", "notes", Autofill.Off))
    contact.position.left @= 400.0

    val search = new SearchField
    val composer = new Composer(draft => Platform.log("autofill sent " + draft.text), width = 640.0)
    val seats = new TextInput("Seats")
    seats.numeric @= true
    seats.value.attach(v => Platform.log("autofill seats=" + v))
    val pin = new TextInput("PIN", secure = true)
    pin.numeric @= true
    pin.value.attach(v => Platform.log("autofill pin of " + v.length.toString))
    val off = new Button("Unhinted off", () => Autofill.fallback @= (if (Autofill.fallback().isEmpty) Some(Autofill.Off) else None))
    val remove = new Button("Remove sign-in", () => if (ui.children().contains(signIn)) {
      ui.children -= signIn
      signIn.dispose()
    })
    // a sign-in put up afresh, as an app shows one long after it started (a card in a chat), for autofill to be asked
    // about it as it was about the first
    val again = new Button("New sign-in", () => if (!ui.children().contains(signIn)) {
      signIn = signInForm()
      ui.children += signIn
    })
    val unhinted = Column(12.0)(search, composer, seats, pin, off, remove, again)
    unhinted.position.left @= 40.0
    unhinted.position.top @= 420.0

    contact.position.top @= 40.0
    // with `later=1` the sign-in is put up a few seconds after the rest, as a card in a chat is long after launch:
    // after Android has asked about the fields there first
    if (Platform.launchArg("later") == "1") {
      ui.children ++= List(contact, unhinted)
      Platform.after(3.0, () => ui.children += signIn)
    }
    else ui.children ++= List(signIn, contact, unhinted)
  }

  private def field(label: String, name: String, hint: Autofill): TextInput = {
    val input = new TextInput(label)
    input.autofill @= Some(hint)
    input.fieldName @= name
    input.value.attach(v => Platform.log("autofill " + name + "=" + v))
    input
  }
}
