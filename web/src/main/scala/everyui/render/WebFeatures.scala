package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** What the browser has, asked of the browser itself and never of its name, so an older engine (a 2016 TV's Chromium
  * 38, webOS 3's, the floor; a 2018 TV's 53) takes a plainer way where it lacks something, and every newer one keeps
  * its own. Each answer is asked once. A global is looked up on the window: a name the engine has never heard of,
  * read bare, throws. */
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

  /** The page address's `?name=` (URLSearchParams, Chromium 49; else read here, `+` as a space), None where it has
    * none. */
  def query(name: String): Option[String] = {
    val search = dom.window.location.search
    global("URLSearchParams") match {
      case Some(made) => Option(js.Dynamic.newInstance(made)(search).get(name).asInstanceOf[String])
      case None => search.stripPrefix("?").split('&').iterator.map(_.split("=", 2)).collectFirst {
        case pair if decoded(pair(0)) == name => if (pair.length > 1) decoded(pair(1)) else ""
      }
    }
  }
  private def decoded(s: String): String =
    try js.URIUtils.decodeURIComponent(s.replace('+', ' ')) catch { case _: Throwable => s }

  /** A key's name as the web names it (`KeyboardEvent.key`, Chromium 51), else from its code and Shift
    * (`KeyCodes`). */
  def key(e: dom.KeyboardEvent): String = {
    val named = e.asInstanceOf[js.Dynamic].key
    if (js.typeOf(named) == "string" && named.asInstanceOf[String].nonEmpty) named.asInstanceOf[String]
    else KeyCodes.name(e.keyCode, e.shiftKey)
  }

  /** A listener's options: the object, where the engine reads one (Chromium 49), else whether it captures, which is
    * all an older engine reads of its third argument (an object there is taken for `true`). */
  def listening(capture: Boolean, passive: Boolean): dom.EventListenerOptions =
    (if (listenerObjects) js.Dynamic.literal(capture = capture, passive = passive) else capture: js.Any)
      .asInstanceOf[dom.EventListenerOptions]
  private lazy val listenerObjects: Boolean = {
    var read = false
    val options = js.Object.defineProperty(js.Object(), "capture", js.Dynamic.literal(
      get = ((() => { read = true; false }): js.Function0[Boolean])).asInstanceOf[js.PropertyDescriptor])
    val nothing: js.Function1[dom.Event, Unit] = _ => ()
    try dom.window.addEventListener("everyui-probe", nothing, options.asInstanceOf[dom.EventListenerOptions])
    catch { case _: Throwable => () }
    dom.window.removeEventListener("everyui-probe", nothing, options.asInstanceOf[dom.EventListenerOptions])
    read
  }

  /** `var()` and custom properties (Chromium 49): elsewhere what they carry is written in directly. */
  lazy val customProperties: Boolean = css("--everyui", "1")

  /** The name the page knows `filter` by: unprefixed from Chromium 53, `-webkit-filter` before it. */
  lazy val filter: String = if (css("filter", "blur(1px)")) "filter" else "-webkit-filter"

  /** The nearest of `el` and those holding it that `selector` matches (`closest`, Chromium 41; else asked of each
    * by `matches`, or `webkitMatchesSelector` before Chromium 34), or null. */
  def closest(el: dom.Element, selector: String): dom.Element =
    if (js.typeOf(el.asInstanceOf[js.Dynamic].closest) == "function") el.closest(selector)
    else {
      var at: dom.Node = el
      while (at != null && !(at.nodeType == dom.Node.ELEMENT_NODE && matches(at.asInstanceOf[dom.Element], selector)))
        at = at.parentNode
      at.asInstanceOf[dom.Element]
    }
  private def matches(el: dom.Element, selector: String): Boolean = {
    val d = el.asInstanceOf[js.Dynamic]
    val asked = if (js.typeOf(d.matches) == "function") d.matches(selector) else d.webkitMatchesSelector(selector)
    asked.asInstanceOf[Boolean]
  }

  /** Tell `f` as `query` (a `matchMedia`) comes to match or stops: its `change` event (Chromium 45), else its
    * listener. */
  def changes(query: dom.MediaQueryList)(f: () => Unit): Unit = {
    val heard: js.Function1[dom.Event, Unit] = _ => f()
    if (js.typeOf(query.asInstanceOf[js.Dynamic].addEventListener) == "function")
      query.addEventListener("change", heard)
    else query.asInstanceOf[js.Dynamic].addListener(heard)
  }
}
