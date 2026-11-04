package everyui.render

import everyui.async.Task
import everyui.auth.SignInCallback

/** Signing in with another site (`everyui.auth.SignIn`): a television has no browser to show the provider's page in,
  * so every sign-in is None at once; a television signs in with a code shown on the screen instead. */
object SignInAccess {
  def supported(): Boolean = false

  def start(callback: SignInCallback, minutes: Double, authorize: String => Task[String],
            done: Option[String] => Unit): Unit = done(None)

  def finish(callback: SignInCallback): Boolean = false
  def resumed(callback: SignInCallback): Option[String] = None
}
