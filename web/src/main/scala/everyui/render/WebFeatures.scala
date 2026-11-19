package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** What the browser has, asked of the browser itself and never of its name, so an older engine (a 2018 TV's Chromium
  * 53, the floor) takes a plainer way where it lacks something, and every newer one keeps its own. Each answer is asked
  * once. A global is looked up on the window: a name the engine has never heard of, read bare, throws. */
object WebFeatures {
  /** Whether the window has `name` (a constructor, a function, an object). */
  def has(name: String): Boolean = js.typeOf(dom.window.asInstanceOf[js.Dynamic].selectDynamic(name)) != "undefined"

  /** The window's `name`, or None where the engine has none. */
  def global(name: String): Option[js.Dynamic] =
    if (has(name)) Some(dom.window.asInstanceOf[js.Dynamic].selectDynamic(name)) else None

  /** Whether the page's style takes `property: value` (`CSS.supports`, Chromium 28). */
  def css(property: String, value: String): Boolean =
    try dom.window.asInstanceOf[js.Dynamic].CSS.supports(property, value).asInstanceOf[Boolean]
    catch { case _: Throwable => false }

  /** Whether `node` is on the page: `isConnected` (Chromium 54), else whether the page holds it. */
  def connected(node: dom.Node): Boolean =
    if (nodesConnect) node.isConnected else node.ownerDocument != null && node.ownerDocument.documentElement.contains(node)
  private lazy val nodesConnect = js.typeOf(dom.document.asInstanceOf[js.Dynamic].isConnected) == "boolean"

  /** Pointer events (Chromium 55): where there are none, the mouse's events stand in (a finger's tap comes as the mouse's
    * press, release and click on a touch screen). */
  lazy val pointerEvents: Boolean = has("PointerEvent")

  /** The page's event for a pointer's `kind` ("down", "up", "move", "enter", "leave", "over", "out", "cancel"): the
    * pointer's, else the mouse's; None for "cancel" where only the mouse's are, which has none. */
  def pointer(kind: String): Option[String] =
    if (pointerEvents) Some("pointer" + kind) else if (kind == "cancel") None else Some("mouse" + kind)

  /** Whether `e` is a finger's pointer event: never where there are none, and the mouse's stand in. */
  def finger(e: dom.Event): Boolean = e.asInstanceOf[js.Dynamic].pointerType.asInstanceOf[Any] == "touch"

  /** `translate`, `rotate` and `scale` as properties of their own (Chromium 104), beside `transform`. */
  lazy val transformProperties: Boolean = css("scale", "2")

  /** The name the page knows `user-select` by: unprefixed from Chromium 54, `-webkit-` before it and in Safari. */
  lazy val userSelect: String = if (css("user-select", "none")) "user-select" else "-webkit-user-select"

  /** `overflow: clip` (Chromium 90): elsewhere `hidden`, which clips alike and makes a scroll box no one scrolls. */
  lazy val overflowClip: String = if (css("overflow", "clip")) "clip" else "hidden"

  /** `f` at the end of the script's turn: `queueMicrotask` (Chromium 71), else a promise's reaction, which runs there
    * too. */
  def microtask(f: () => Unit): Unit = {
    val task: js.Function0[Unit] = () => f()
    if (has("queueMicrotask")) js.Dynamic.global.queueMicrotask(task)
    else js.Dynamic.global.Promise.resolve().`then`(task)
  }

  /** A fetch's options given an abort of their own, and what ends the request once its answer (`Response`) is in: the
    * abort's signal (AbortController, Chromium 66), else the answer's body cancelled, which stops its download too. */
  def abortable(init: js.Dynamic): js.Dynamic => Unit = global("AbortController") match {
    case Some(made) =>
      val abort = js.Dynamic.newInstance(made)()
      init.updateDynamic("signal")(abort.signal)
      _ => abort.abort()
    case None => r => if (r != null && js.typeOf(r.body) == "object" && r.body != null) r.body.cancel()
  }

  /** Tell `f` as `el` changes size: a ResizeObserver (Chromium 64), else as the window does and as `also` (the
    * element's own events: "input", "scroll") are heard, after the frame they come in. */
  def resized(el: dom.Element, also: List[String] = Nil)(f: () => Unit): Unit = global("ResizeObserver") match {
    case Some(made) =>
      val heard: js.Function1[js.Any, Unit] = _ => f()
      js.Dynamic.newInstance(made)(heard).observe(el)
    case None =>
      var due = false
      val look: js.Function1[dom.Event, Unit] = _ => if (!due) {
        due = true
        dom.window.requestAnimationFrame(_ => { due = false; f() })
      }
      dom.window.addEventListener("resize", look)
      also.foreach(el.addEventListener(_, look))
      look(null)
  }
}
