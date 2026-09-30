package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** A page of the web in an `<iframe>` (Nodes.web): the browser is the engine it lends.
  *
  * A page of the app's own origin is told things as the other backends' engines tell it, by running `run`'s script in
  * it (its window's `eval`), and it answers through `<bridge>.postMessage("...")`, an object put on its window as it
  * loads. A page of another origin cannot be reached into, so the two talk only by `window.postMessage`: `run` posts
  * `{everyui: "run", script}` to it, addressed to the origin of `source` alone, for the page to act on the commands
  * it knows and nothing else, and what it posts to its parent (a string, or an object, which is heard as its JSON) is
  * heard as its bridge's messages are. Whatever else posts to the app's window is not heard. */
class WebFrame(val frame: html.IFrame, bridge: String) {
  private var hearing: String => Unit = _ => ()
  private var origin: String = ""
  private var loaded: Boolean = false
  private var waiting: List[String] = Nil
  private var released: Boolean = false

  private val heard: js.Function1[dom.MessageEvent, Unit] = (e: dom.MessageEvent) =>
    if (e.source != null && (e.source eq frame.contentWindow) && e.origin == origin) said(e.data.asInstanceOf[js.Any])

  frame.addEventListener("load", (_: dom.Event) => if (origin.nonEmpty) {
    loaded = true
    lend()
    val scripts = waiting.reverse
    waiting = Nil
    scripts.foreach(run)
  })
  dom.window.addEventListener("message", heard)

  /** Show this address. */
  def source(url: String): Unit = if (url.trim.nonEmpty) {
    origin = new dom.URL(url.trim, dom.document.baseURI).origin
    loaded = false
    frame.src = url.trim
    early(600)
  }

  // the bridge lent as soon as the page loading has a window of its own, looked for a frame at a time until it has
  // loaded (for `frames` at most), so what the page says before then is heard too; a script run as the page is read
  // may still be earlier than that
  private def early(frames: Int): Unit = if (!loaded && !released && frames > 0) {
    ownWindow.foreach { w =>
      if (w.location.href != "about:blank" && js.isUndefined(w.asInstanceOf[js.Dynamic].selectDynamic(bridge))) lend()
    }
    dom.window.requestAnimationFrame(_ => early(frames - 1))
  }

  def hear(f: String => Unit): Unit = hearing = f

  /** Say something to the page: run in it where it is the app's own origin, posted to it where it is not; kept until
    * it has loaded. */
  def run(script: String): Unit =
    if (!loaded) waiting = script :: waiting
    else ownWindow match {
      case Some(w) =>
        try w.asInstanceOf[js.Dynamic].applyDynamic("eval")(script)
        catch { case e: Throwable => Platform.log("[web] a script in the page failed: " + e.getMessage) }
      case None =>
        val window = frame.contentWindow
        if (window != null && origin != "null") window.postMessage(js.Dynamic.literal(everyui = "run", script = script), origin)
    }

  /** Done with: the page is let go (what it plays stops with it) and the app's window stops listening for it. */
  def release(): Unit = {
    released = true
    dom.window.removeEventListener("message", heard)
    hearing = _ => ()
    waiting = Nil
    origin = ""
    frame.src = "about:blank"
  }

  // the page's window, where it is the app's own origin: reaching another's document is refused (a SecurityError)
  private def ownWindow: Option[dom.Window] = {
    val w = frame.contentWindow
    try if (w != null && w.document != null) Some(w) else None
    catch { case _: Throwable => None }
  }

  // the bridge put on the page's window, where the page is the app's own; a page loading has a new window, so again
  // each time it loads
  private def lend(): Unit = ownWindow.foreach { w =>
    val say: js.Function1[js.Any, Unit] = (message: js.Any) => said(message)
    w.asInstanceOf[js.Dynamic].updateDynamic(bridge)(js.Dynamic.literal(postMessage = say))
  }

  private def said(message: js.Any): Unit = hearing(
    if (js.typeOf(message) == "string") message.asInstanceOf[String] else js.JSON.stringify(message)
  )
}
