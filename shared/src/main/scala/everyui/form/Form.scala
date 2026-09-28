package everyui.form

import everyui.component.Container
import everyui.render.{Nodes, Platform}
import reactify.*

/** Fields that belong together — a sign-in, a sign-up, an address — as the platform's autofill and password managers
  * want them: one form they fill as a whole, whose sending they see, and whose success is their cue to offer to save
  * what was typed. Put the fields in it (with their `autofill` hints), send it with `submit()` (the button, a field's
  * Enter), act on `submitted`, and say how it went: `succeeded()` once the server accepted it, `abandoned()` when the
  * user gave up. A form taken away unsaid is abandoned.
  *
  * On the web it is a real `<form>`: Enter in a field and a password manager clicking its submit button send it too,
  * never the page, and `succeeded()` takes its fields out of the page, which the browser and the managers read as a
  * sign-in done. On Android `succeeded()` commits the autofill session (the save prompt) and `abandoned()` cancels it;
  * on Apple a sign-in's fields leaving the screen is the cue. Elsewhere it is a container.
  *
  * {{{
  *   val form = new Form(FormKind.SignIn)
  *   val email = new TextInput("Email") { autofill @= Some(Autofill.Username) }
  *   val password = new TextInput("Password", secure = true) { autofill @= Some(Autofill.CurrentPassword) }
  *   password.submitted.attach(_ => form.submit())
  *   form.submitted.attach(_ => api.signIn(email.value(), password.value()).map {
  *     case true => form.succeeded(); show(Home)
  *     case false => error @= "Wrong email or password"
  *   })
  *   form.children ++= List(email, password, new Button("Sign in", () => form.submit()))
  * }}} */
class Form(val purpose: FormKind = FormKind.Other) extends Container(Nodes.form(Form.name(purpose))) {
  /** It was sent: its button, Enter in a field, or a password manager filling and sending it. */
  val submitted: Channel[Unit] = Channel[Unit]
  /** The account a password-only step is for, where the username was asked a step before: the password manager saves
    * the password against it. Empty for none. */
  val hiddenUsername: Var[String] = own(Var(""))

  private var sentAt = -1000.0
  private var done = false

  /** Send it: once, however many ways one press reaches it (a field's Enter and the page's own sending). */
  def submit(): Unit = {
    val now = Platform.ticks()
    if (now - sentAt > 300.0) {
      sentAt = now
      submitted @= (())
    }
  }

  /** What it was given was accepted (the sign-in worked): the cue to offer to save it. The form has then given its
    * fields up; show the next screen, or another form. */
  def succeeded(): Unit = if (!done) {
    done = true
    node.formDone(true)
  }

  /** The user gave up on it: nothing is to be saved. */
  def abandoned(): Unit = if (!done) {
    done = true
    node.formDone(false)
  }

  node.onSubmit(() => submit())
  hiddenUsername.attach(v => node.setHiddenUsername(v))

  override def dispose(): Unit = {
    abandoned()
    super.dispose()
  }

  override def kind: String = "Form"
}

object Form {
  /** Its kind by the seam's name: "signin", "signup", "change-password", "contact", "payment" or "other". */
  def name(kind: FormKind): String = kind match {
    case FormKind.SignIn => "signin"
    case FormKind.SignUp => "signup"
    case FormKind.ChangePassword => "change-password"
    case FormKind.Contact => "contact"
    case FormKind.Payment => "payment"
    case FormKind.Other => "other"
  }
}
