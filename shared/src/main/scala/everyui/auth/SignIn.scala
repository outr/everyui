package everyui.auth

import everyui.async.Task
import everyui.render.SignInAccess

/** Signing in with another site (OAuth: Google, Microsoft, Slack), one way on every platform: the provider's page shown
  * where the platform shows one for signing in, and the address it ends at, the redirect with its code or its error,
  * handed back.
  *
  * The redirect differs by platform (`SignInCallback`: a page of the app's own on the web, the app's scheme on a phone,
  * a port at 127.0.0.1 chosen for each sign-in on the desktop), so the app is given it and answers with the address to
  * sign in at, usually from its server, which puts it in the request it makes the provider:
  *
  * {{{
  *   SignIn.signInWith(callback)(redirect => server.authorizeUrl("google", redirect)).map {
  *     case Some(ended) => server.finishSignIn(ended)   // the redirect, ?code=…&state=…
  *     case None => ()                                  // closed, refused, or no answer in time
  *   }.start()
  * }}}
  *
  * Start it from a press: the web opens its window in it, before the address has come. None where the person closed
  * the window, the address was "", `minutes` passed with no answer, or the platform has nowhere to sign in (a Roku, an
  * Apple TV). Answered once. */
object SignIn {
  /** Whether this platform can sign in with another site. */
  def supported: Boolean = SignInAccess.supported()

  /** The provider's page at the address `authorize` answers for this platform's redirect, and where it ends. */
  def signInWith(callback: SignInCallback, minutes: Double = 10.0)(
      authorize: String => Task[String]): Task[Option[String]] =
    Task.async[Option[String]](done => SignInAccess.start(callback, minutes, authorize, done))

  /** On the web, in the callback's page as the app starts there: the address it was sent to handed back to the window
    * that asked, and the window closed, true; false where this is not the callback's page, or where the sign-in left
    * the whole page (a browser that would open no window), for `resumed` to answer. Elsewhere false. */
  def finish(callback: SignInCallback): Boolean = SignInAccess.finish(callback)

  /** On the web, where a browser that would open no window sent the whole page to sign in: the address it came back
    * to, once, as the app starts again at the callback, the address bar put back to where the sign-in began. None
    * anywhere else. */
  def resumed(callback: SignInCallback): Option[String] = SignInAccess.resumed(callback)
}
