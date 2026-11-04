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
  * heard as its bridge's messages are. Whatever else posts to the app's window is not heard.
  *
  * A sandboxed page (`sandbox`, the iframe's own attribute) is neither told nor heard, whatever its origin: nothing is
  * lent it, run in it or posted to it, and nothing it posts is heard; unless the app asked to hear it
  * (`hearSandboxed`), when what it posts from this frame alone is heard, and still nothing is lent or run.
  *
  * `ask` posts `{everyui: "ask", session, id, request}` and hears `{everyui: "reply", session, id, reply}` back, the
  * session the one this frame's `source` was given, so a page that is no longer showing is not heard answering. What
  * the frames inside the page post is heard only where the app asked (`nested`), with their origin. */
class WebFrame(val frame: html.IFrame, bridge: String) {
  private var hearing: String => Unit = _ => ()
  private var replying: (Int, String) => Unit = (_, _) => ()
  private var nestedHearing: Option[(String, String) => Unit] = None
  private var origin: String = ""
  private var loaded: Boolean = false
  private var waiting: List[String] = Nil
  private var released: Boolean = false
  private var current: String = ""
  private var sandboxed: Boolean = false
  private var hearsSandboxed: Boolean = false
  private var session: String = ""

  private val heard: js.Function1[dom.MessageEvent, Unit] = (e: dom.MessageEvent) =>
    if (e.source != null && (e.source eq frame.contentWindow)) {
      val fromPage = if (sandboxed) hearsSandboxed && (e.origin == "null" || e.origin == origin) else e.origin == origin
      if (fromPage) said(e.data.asInstanceOf[js.Any])
    }
    else if (e.source != null && !released) nestedHearing.foreach { f =>
      if (inside(e.source.asInstanceOf[js.Dynamic])) f(e.origin, text(e.data.asInstanceOf[js.Any]))
    }

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
    current = url.trim
    origin = new dom.URL(url.trim, dom.document.baseURI).origin
    loaded = false
    session = java.lang.Long.toString((math.random() * 1.0e12).toLong, 36)
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

  /** What a sandboxed page posts from this frame heard as well (`on`), though nothing is lent it or run in it. */
  def hearSandboxed(on: Boolean): Unit = hearsSandboxed = on

  /** The replies to `ask`, by the id each was asked with. */
  def hearReplies(f: (Int, String) => Unit): Unit = replying = f

  /** What the frames inside the page post, with the origin of each, heard by `f`; None hears them no more. */
  def nested(f: Option[(String, String) => Unit]): Unit = nestedHearing = f

  /** The frame sandboxed with `flags` allowed (None: not at all), and the page loaded again where it shows one, as a
    * browser only honours the attribute as the frame navigates. */
  def sandbox(flags: Option[Set[String]]): Unit = {
    val was = Option(frame.getAttribute("sandbox"))
    val now = flags.map(_.toList.sorted.mkString(" "))
    sandboxed = flags.nonEmpty
    now match {
      case Some(s) => frame.setAttribute("sandbox", s)
      case None => frame.removeAttribute("sandbox")
    }
    if (was != now && current.nonEmpty && !released) source(current)
  }

  /** Say something to the page: run in it where it is the app's own origin, posted to it where it is not; kept until
    * it has loaded. */
  def run(script: String): Unit =
    if (sandboxed) ()
    else if (!loaded) waiting = script :: waiting
    else ownWindow match {
      case Some(w) =>
        try w.asInstanceOf[js.Dynamic].applyDynamic("eval")(script)
        catch { case e: Throwable => Platform.log("[web] a script in the page failed: " + e.getMessage) }
      case None =>
        val window = frame.contentWindow
        if (window != null && origin != "null") window.postMessage(js.Dynamic.literal(everyui = "run", script = script), origin)
    }

  /** Ask the page `request`, posted to it as `{everyui: "ask", session, id, request}`, whatever its origin: a sandboxed
    * page only where it is heard (`hearSandboxed`), and then to any origin, since it has none of its own. False where
    * it could not be posted (no page, or one sandboxed and not heard). */
  def ask(id: Int, request: String): Boolean = {
    val window = frame.contentWindow
    if (released || window == null || current.isEmpty || (sandboxed && !hearsSandboxed)) false
    else {
      val to = if (sandboxed || origin == "null") "*" else origin
      window.postMessage(js.Dynamic.literal(everyui = "ask", session = session, id = id, request = request), to)
      true
    }
  }

  /** Done with: the page is let go (what it plays stops with it) and the app's window stops listening for it. */
  def release(): Unit = {
    released = true
    dom.window.removeEventListener("message", heard)
    hearing = _ => ()
    replying = (_, _) => ()
    nestedHearing = None
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

  // whether `source` is a window inside the page's, at any depth: a window's parent may be read whatever its origin
  private def inside(source: js.Dynamic): Boolean = {
    val page = frame.contentWindow.asInstanceOf[js.Dynamic]
    var at = source
    var depth = 0
    var found = false
    while (!found && depth < 16 && at != null && !js.isUndefined(at)) {
      val up = try at.parent catch { case _: Throwable => null }
      if (up == null || js.isUndefined(up) || (up eq at)) at = null
      else {
        found = up eq page
        at = up
      }
      depth += 1
    }
    found && page != null
  }

  // the bridge put on the page's window, where the page is the app's own; a page loading has a new window, so again
  // each time it loads
  private def lend(): Unit = if (!sandboxed) ownWindow.foreach { w =>
    val say: js.Function1[js.Any, Unit] = (message: js.Any) => said(message)
    w.asInstanceOf[js.Dynamic].updateDynamic(bridge)(js.Dynamic.literal(postMessage = say))
    // the mouse's back and forward buttons over it are the app's, as over the page
    NavButtons.listen(w)
  }

  // a reply to `ask` (an object, or its JSON) goes to whatever asked, where it is this page's; anything else is heard
  private def said(message: js.Any): Unit = {
    val data: js.Any =
      if (js.typeOf(message) == "string" && message.asInstanceOf[String].startsWith("{\"everyui\":\"reply\""))
        try js.JSON.parse(message.asInstanceOf[String]) catch { case _: Throwable => message }
      else message
    val d = data.asInstanceOf[js.Dynamic]
    if (js.typeOf(data) == "object" && data != null && (d.everyui: Any) == "reply") {
      val id = d.id.asInstanceOf[js.Any]
      if ((d.session: Any) == session && js.typeOf(id) == "number")
        replying(id.asInstanceOf[Double].toInt, text(d.reply.asInstanceOf[js.Any]))
    }
    else hearing(text(message))
  }

  private def text(message: js.Any): String =
    if (js.typeOf(message) == "string") message.asInstanceOf[String]
    else if (js.isUndefined(message)) ""
    else js.JSON.stringify(message)
}
