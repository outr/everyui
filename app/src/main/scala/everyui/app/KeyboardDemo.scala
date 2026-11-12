package everyui.app

import everyui.component.{Column, TextInput}
import everyui.component.types.Autofill
import everyui.kit.SearchField
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** The platform's keyboard for fields with no field of their own (a Roku's StandardKeyboardDialog, an Apple TV's text
  * dialog): an email address, a password, a PIN of digits, hidden, and a search box, the email focused. OK on one
  * opens the keyboard its kind asks for (on a Roku its `keyboardDomain`: email, password, numeric, generic), holding
  * the value; Done brings the words back to the field, and Cancel or Back leave it as it was, the focus on the field
  * either way. Each value and each submit is logged, a secret's by its length:
  *
  *   [ui] keyboard email=ann@example.com
  *   [ui] keyboard email submitted
  *   [ui] keyboard password of 6
  *   [ui] keyboard pin of 4
  *   [ui] keyboard search=news
  *
  * and on a Roku the runtime says what it opened and how it closed:
  *
  *   [keyboard] open: domain email, secure false, 0 characters, 'Email'
  *   [keyboard] done: 15 characters
  *   [keyboard] cancelled
  *
  * It stands for a sign-in before an app's home, as certification times one: it sends `AppDialogInitiate` as it comes
  * up, and `AppDialogComplete` and then `AppLaunchComplete` the first time the email address is submitted (`[beacon]
  * AppDialogInitiate` on the console, and the device's own `[beacon.signal]` lines on its debug console).
  */
object KeyboardDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val width = math.min(480.0, ui.size.width() - 32.0)
    val email = new TextInput("Email", width = width)
    email.autofill @= Some(Autofill.Email)
    email.value.attach(v => Platform.log("keyboard email=" + v))
    val signedIn = Var(false)
    email.submitted.attach { _ =>
      Platform.log("keyboard email submitted")
      if (!signedIn()) {
        signedIn @= true
        Platform.beacon("AppDialogComplete")
        Platform.launchComplete()
      }
    }
    val password = new TextInput("Password", secure = true, width = width)
    password.autofill @= Some(Autofill.CurrentPassword)
    password.value.attach(v => Platform.log("keyboard password of " + v.length.toString))
    val pin = new TextInput("PIN", secure = true, width = width)
    pin.numeric @= true
    pin.value.attach(v => Platform.log("keyboard pin of " + v.length.toString))
    val search = new SearchField(width = width)
    search.value.attach(v => Platform.log("keyboard search=" + v))
    val body = Column(14.0)(email, password, pin, search)
    body.position.left @= 40.0
    body.position.top @= 40.0
    ui.children += body
    email.focus()
    Platform.beacon("AppDialogInitiate")
  }
}
