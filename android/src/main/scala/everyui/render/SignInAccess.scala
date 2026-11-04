package everyui.render

import android.content.{ActivityNotFoundException, Intent}
import android.net.Uri
import everyui.async.Task
import everyui.auth.SignInCallback

/** Signing in with another site on Android (`everyui.auth.SignIn`): the provider in a Custom Tab, the browser's own
  * window over the app (Chrome's and the others' that take the Custom Tabs extras, asked for without the library),
  * and the redirect to the app's own scheme (`SignInCallback.scheme`), which the app's manifest sends back to its
  * activity (`EveryUIActivity.onNewIntent`, `returned`). The person back in the app with no redirect (the tab closed,
  * Back) is None. Where nothing can show the page (a television without a browser) it is None at once. */
object SignInAccess {
  private var waiting: Option[(String, Option[String] => Unit)] = None
  private var stopResume: () => Unit = () => ()

  def supported(): Boolean = true

  def start(callback: SignInCallback, minutes: Double, authorize: String => Task[String],
            done: Option[String] => Unit): Unit =
    if (callback.scheme.isEmpty) {
      Platform.log("SignIn: no scheme for the app to come back to (SignInCallback.scheme)")
      done(None)
    }
    else {
      end(None)
      val redirect = callback.scheme + ":" + callback.path
      authorize(redirect).map { url =>
        if (url.isEmpty) done(None)
        else {
          waiting = Some(redirect -> done)
          // the redirect comes before the activity resumes (onNewIntent, then onResume): resumed without it is the
          // person come back without signing in
          stopResume = Platform.onResume(() => Platform.after(0.5, () => end(None)))
          Platform.after(minutes * 60.0, () => if (waiting.exists(_._2 eq done)) end(None))
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
          val extras = new android.os.Bundle()
          extras.putBinder("android.support.customtabs.extra.SESSION", null)
          intent.putExtras(extras)
          try Host.activity.startActivity(intent)
          catch {
            case _: ActivityNotFoundException =>
              Platform.log("SignIn: nothing here shows " + url)
              end(None)
          }
        }
      }.start()
    }

  /** An address the activity was opened with: the sign-in's redirect, where one is waiting for it (true). */
  def returned(address: String): Boolean = waiting match {
    case Some((redirect, _)) if address.startsWith(redirect) =>
      end(Some(address))
      true
    case _ => false
  }

  private def end(result: Option[String]): Unit = waiting.foreach { case (_, done) =>
    waiting = None
    stopResume()
    stopResume = () => ()
    done(result)
  }

  /** A phone has no callback page: the redirect is the app's scheme. */
  def finish(callback: SignInCallback): Boolean = false
  def resumed(callback: SignInCallback): Option[String] = None
}
