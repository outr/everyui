package everyui.app

import everyui.app.screen.{FormScreen, ScreenManager}
import everyui.component.*
import everyui.component.types.Autofill
import everyui.net.*
import everyui.render.Platform
import reactify.*

/** The TV demo's sign-in form (TvDemo), as an app's is: an email and a password, then Sign in. Enter on the email goes
  * on to the password, and on the password signs in; each submit, and the editing starting and ending, is logged:
  *
  *   [ui] tv: editing Email
  *   [ui] tv: done editing Email
  *   [ui] tv: signed in as a@b.c (password 6 characters)
  */
class TvSignInScreen extends FormScreen {
  override def manager: ScreenManager = TvDemo
  override def heading: Val[String] = Val("Sign in")
  override def path: URLPath = path"/sign-in"

  private val email: TextInput = field("Email", Var(""))(password.focus())
  private val password: TextInput = field("Password", Var(""), secure = true)(signIn())

  override def defaultFocus(): Component = email

  override protected def build(): Unit = {
    email.autofill @= Some(Autofill.Email)
    password.autofill @= Some(Autofill.CurrentPassword)
    List(email, password).foreach(f =>
      f.editing.attach(e => Platform.log("tv: " + (if (e) "editing " else "done editing ") + f.title())))
    fields.children ++= List(email, password)
    actions.children += primary("Sign in")(signIn())
  }

  private def signIn(): Unit = Platform.log("tv: signed in as " + email.value() + " (password " +
    password.value().length.toString + " characters)")
}
