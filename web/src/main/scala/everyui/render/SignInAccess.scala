package everyui.render

import everyui.async.Task
import everyui.auth.SignInCallback
import org.scalajs.dom

import scala.scalajs.js

/** Signing in with another site on the web (`everyui.auth.SignIn`): a window of its own, opened in the press, blank
  * and kept apart from the app (no `opener`) until the address comes, then sent there. The provider ends at the
  * callback, a page of the app's own origin, which says the address it was sent to on a BroadcastChannel only pages of
  * that origin hear, and closes. A window the person closes is None, judged once it is closed and the app's window has
  * the focus again: a provider whose page parts it from its opener (Cross-Origin-Opener-Policy) makes it look closed
  * while it is still open, and the person is still in it then.
  *
  * Where the browser opens no window, the whole page goes to sign in, its path kept for this tab, and comes back to the
  * callback, where `resumed` answers what it came back to. */
object SignInAccess {
  private val channel = "everyui-signin"
  private val pendingKey = "everyui-signin-from"
  private val endedKey = "everyui-signin-ended"

  def supported(): Boolean = true

  def start(callback: SignInCallback, minutes: Double, authorize: String => Task[String],
            done: Option[String] => Unit): Unit = {
    val redirect = dom.window.location.origin + callback.web
    val w = dom.window
    val features = "popup,width=520,height=700,left=" + (w.screenX + math.max(0.0, (w.outerWidth - 520) / 2.0)).toInt +
      ",top=" + (w.screenY + math.max(0.0, (w.outerHeight - 700) / 2.0)).toInt
    val held = w.open("", "everyui-signin", features)
    if (held == null || js.isUndefined(held)) authorize(redirect).map { url =>
      if (!everyui.component.NavigationUrl.safe(url) || url.isEmpty) done(None)
      else {
        session(_.setItem(pendingKey, Platform.path()))
        w.location.assign(url)
      }
    }.start()
    else {
      held.asInstanceOf[js.Dynamic].opener = null
      val heard = js.Dynamic.newInstance(js.Dynamic.global.BroadcastChannel)(channel)
      var finished = false
      var watch = 0
      var closedSeen = false
      var stopFocus: () => Unit = () => ()
      def end(result: Option[String]): Unit = if (!finished) {
        finished = true
        heard.close()
        w.clearInterval(watch)
        stopFocus()
        try held.close() catch { case _: Throwable => () }
        done(result)
      }
      heard.onmessage = (e: dom.MessageEvent) => {
        val m = e.data.asInstanceOf[js.Dynamic]
        if (!js.isUndefined(m) && m != null && (m.everyui: Any) == "signin") {
          val url = m.url.asInstanceOf[String]
          if (url != null && url.startsWith(redirect)) end(Some(url))
        }
      }
      // closed and the person back: given a moment for the callback's word, which may still be on its way
      def judge(): Unit = if (closedSeen && dom.document.hasFocus()) w.setTimeout(() => end(None), 1500)
      watch = w.setInterval(() => if (!closedSeen && held.closed) {
        closedSeen = true
        judge()
      }, 500)
      stopFocus = Platform.onFocus(() => judge())
      w.setTimeout(() => end(None), minutes * 60000.0)
      authorize(redirect).map { url =>
        if (url.isEmpty || !everyui.component.NavigationUrl.safe(url)) end(None) else held.location.href = url
      }.start()
    }
  }

  def finish(callback: SignInCallback): Boolean =
    if (dom.window.location.pathname != callback.web) false
    else if (session(s => Option(s.getItem(pendingKey))).flatten.nonEmpty) {
      session(_.setItem(endedKey, dom.window.location.href))
      false
    }
    else {
      val said = js.Dynamic.newInstance(js.Dynamic.global.BroadcastChannel)(channel)
      said.postMessage(js.Dynamic.literal(everyui = "signin", url = dom.window.location.href))
      said.close()
      dom.window.close()
      true
    }

  def resumed(callback: SignInCallback): Option[String] =
    if (dom.window.location.pathname != callback.web) None
    else session { s =>
      val from = Option(s.getItem(pendingKey))
      s.removeItem(pendingKey)
      val ended = Option(s.getItem(endedKey)).getOrElse(dom.window.location.href)
      s.removeItem(endedKey)
      from.map { path =>
        dom.window.history.replaceState(dom.window.history.state, "", path)
        ended
      }
    }.flatten

  private def session[T](f: dom.Storage => T): Option[T] =
    try Some(f(dom.window.sessionStorage)) catch { case _: Throwable => None }
}
