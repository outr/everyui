package everyui.app

import everyui.auth.AccountDetails
import everyui.component.{Button, Column, TextView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** The person's account details asked for as a sign-up or a sign-in form would (`AccountDetails`): `demo=userdata`.
  * Share email asks for the email address as for signing in, Share name and email the email address and the name as
  * for signing up; on a Roku each brings up Roku's own dialog (ChannelStore getUserData), and elsewhere answers that
  * nothing was shared. What came back is shown and logged:
  *
  *   [ui] userdata supported=true
  *   [ui] userdata email: shared email=someone@example.com
  *   [ui] userdata name and email: nothing shared
  *
  * `auto=1` asks for the name and email as it starts, and `auto=email` for the email address. A sideloaded Roku channel
  * launched with `fakestore=1` asks the store's fake server, which answers at once with made-up details. */
object UserDataDemo {
  private val said = TextView("", 16.0, Theme.text())

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    Platform.log("userdata supported=" + AccountDetails.supported.toString)
    said.content @= (if (AccountDetails.supported) "Nothing asked yet" else "No account to ask on this platform")
    val email = new Button("Share email", () => ask("email", List("email"), signIn = true))
    val all = new Button("Share name and email", () =>
      ask("name and email", List("email", "firstname", "lastname"), signIn = false))
    val column = Column(spacing = 16.0)(TextView("Account details", 22.0, Theme.text()), email, all, said)
    column.position.left @= 48.0
    column.position.top @= 40.0
    ui.children += column
    val auto = Platform.launchArg("auto")
    if (auto == "1") ask("name and email", List("email", "firstname", "lastname"), signIn = false)
    else if (auto == "email") ask("email", List("email"), signIn = true)
  }

  private def ask(label: String, fields: List[String], signIn: Boolean): Unit = {
    said.content @= "Asking for " + label + "…"
    AccountDetails.ask(fields, signIn).map { shared =>
      val line = shared match {
        case Some(details) => "shared " + fields.filter(details.contains).map(f => f + "=" + details(f)).mkString(" ")
        case None => "nothing shared"
      }
      said.content @= label + ": " + line
      Platform.log("userdata " + label + ": " + line)
    }.start()
  }
}
