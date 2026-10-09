package everyui.render

import everyui.app.LeaveGuards
import org.scalajs.dom

import scala.scalajs.js

/** The page's guards (`Platform.leaveGuard`) heard as it unloads: closed, reloaded, or another address typed or linked
  * to. While a guard answers true, the browser asks with its own "Leave site?": what it says is its own (no page sets
  * the words any more), and it asks only once the page has had a click or a key from the person. A move within the app
  * (`Platform.pushPath`, the history) unloads nothing, so it is never asked.
  *
  * The listener stands only while a guard might answer true (`LeaveGuards.armed`): a page with one may be kept out of
  * the browser's back and forward cache. */
object PageLeave {
  private val listener: js.Function1[dom.Event, Unit] = (e: dom.Event) => if (LeaveGuards.guarded()) {
    e.preventDefault()
    // what the browsers before preventDefault was enough look for
    e.asInstanceOf[js.Dynamic].returnValue = true
  }
  private var listening = false

  /** Heard from the first guard on. */
  lazy val follow: Unit = {
    LeaveGuards.armed.attachAndFire(listen)
    ()
  }

  private def listen(on: Boolean): Unit = if (on != listening) {
    listening = on
    if (on) dom.window.addEventListener("beforeunload", listener)
    else dom.window.removeEventListener("beforeunload", listener)
  }
}
