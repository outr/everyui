package everyui.auth

import everyui.async.Task
import everyui.render.AccountAccess

/** The person's own details as the platform's account holds them (their email address, their name), shared with the
  * app only where the person agrees, for a sign-up or a sign-in form filled in for them. A Roku asks through the
  * ChannelStore's getUserData, which shows Roku's own dialog naming what the app asks for (its certification wants
  * every channel that signs people in to offer this for the email address); elsewhere there is no such account to ask,
  * and every request answers None on a later turn.
  *
  * {{{
  *   AccountDetails.email().map {
  *     case Some(address) => emailField.text @= address
  *     case None => ()                                   // declined, nothing shared, or not on this platform
  *   }.start()
  * }}}
  *
  * The fields are Roku's names: "email", "firstname", "lastname", "phone", "street", "city", "state", "zip",
  * "country", "birth" and "gender". Start it from a press, as the person reaches the form: the dialog comes up over
  * the app at once. One request at a time: another asked for while one waits answers the first None. */
object AccountDetails {
  /** Whether this platform has an account to ask (a Roku). */
  def supported: Boolean = AccountAccess.supported()

  /** The `fields` the person agreed to share, by name, each a non-empty value; None where they declined or shared
    * none. `signIn` asks as for signing in, where Roku offers the email address (and phone) alone, rather than as for
    * signing up. */
  def ask(fields: Seq[String], signIn: Boolean = false): Task[Option[Map[String, String]]] =
    Task.async[Option[Map[String, String]]](done => AccountAccess.ask(fields, signIn, done))

  /** The person's email address, where they agreed to share it. */
  def email(signIn: Boolean = false): Task[Option[String]] =
    ask(List("email"), signIn).map(shared => shared.flatMap(_.get("email")))
}
