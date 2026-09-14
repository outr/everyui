package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html
import everyui.font.Faces

import scala.scalajs.js

/** **The one invention.** The abstract render node, DOM edition.
  *
  * Signature-for-signature the same as its Roku, Android and JavaFX twins
  * (`roku/`, `android/`, `desktop/` …/everyui/render/RenderNode.scala) — that parallel IS the seam.
  * The shared `Component`/`Label` name only this type, and each backend's
  * source root supplies it at compile time.
  *
  * This backend is REAL Scala.js over the live DOM, not a lighter emission: the
  * web tier's whole argument (DESIGN.md) is that the browser's own semantics —
  * autofill, IME, text selection, find-in-page, the a11y tree — are worth
  * keeping, and that argument only holds if the web backend is genuinely the
  * DOM. It is also why the web project builds with scala-cli rather than sbt:
  * Scala.js has no sbt 2 plugin.
  */
class RenderNode(val el: html.Element):
  def getText(): String = el.textContent
  def setText(text: String): Unit = el.textContent = text

  def getX(): Double = px(el.style.left)
  def setX(x: Double): Unit = el.style.left = x.toString + "px"

  def getY(): Double = px(el.style.top)
  def setY(y: Double): Unit = el.style.top = y.toString + "px"

  /** Radians, clockwise, about the node's own centre — the DOM's `rotate()`
    * is already centre-pivoted by default (`transform-origin: 50% 50%`). */
  def getRotation(): Double = rotation
  def setRotation(radians: Double): Unit =
    rotation = radians
    el.style.transform = "rotate(" + radians.toString + "rad)"

  def setFontSize(size: Double): Unit = el.style.fontSize = size.toString + "px"

  /** `"#rrggbb"`. */
  def setColor(hex: String): Unit = el.style.color = hex

  /** A bundled face (index.html's `@font-face` rules): family, CSS weight,
    * size in pixels. The line box is the shared one (everyui.font.Face), not
    * the browser's `line-height: normal`, so text is the same height on every
    * backend. */
  def setFont(family: String, weight: Int, size: Double): Unit =
    if family.nonEmpty then el.style.fontFamily = "'" + family + "'"
    el.style.fontWeight = weight.toString
    el.style.fontSize = size.toString + "px"
    el.style.lineHeight = Faces(family, weight).lineHeight(size).toString + "px"

  /** The laid-out size in pixels (text is measured with its real font). */
  def getWidth(): Double = el.getBoundingClientRect().width
  def getHeight(): Double = el.getBoundingClientRect().height

  def append(child: RenderNode): Unit = el.appendChild(child.el)

  /** Detach from the parent, if attached. */
  def remove(): Unit = if el.parentNode != null then el.parentNode.removeChild(el)

  def setWidth(width: Double): Unit = el.style.width = width.toString + "px"
  def setHeight(height: Double): Unit = el.style.height = height.toString + "px"

  /** `"#rrggbb"`, or `""` for transparent. */
  def setBackgroundColor(hex: String): Unit =
    el.style.backgroundColor = if hex.isEmpty then "transparent" else hex

  def setRadius(radius: Double): Unit = el.style.borderRadius = radius.toString + "px"

  /** CSS `position`; `""` is static. */
  /** A pointer event of `kind` ("down", "up", "move", "enter", "leave", "click", or "wheel" with its deltas) with its
    * viewport coordinates. */
  def onPointer(kind: String, f: (Double, Double) => Unit): Unit =
    if kind == "wheel" then el.addEventListener("wheel", (e: dom.WheelEvent) => f(e.deltaX, e.deltaY))
    else
      val name = if kind == "click" then "click" else "pointer" + kind
      el.addEventListener(name, (e: dom.MouseEvent) => f(e.clientX, e.clientY))

  /** The node scrolls its own overflow: the browser's scrollbar, wheel, touch and keys. */
  def setScrollable(vertical: Boolean): Unit =
    el.style.overflowY = if vertical then "auto" else "hidden"
    el.style.overflowX = "hidden"
  def setScrollTop(y: Double): Unit = el.scrollTop = y
  /** Clipping is the browser's overflow; a scrollable node already clips. */
  def setClip(width: Double, height: Double): Unit = if el.style.overflowY != "auto" then el.style.overflow = "hidden"
  def setTooltip(text: String): Unit = el.title = text

  def setPositionType(name: String): Unit = el.style.position = if name.isEmpty then "static" else name

  def setWhiteSpace(value: String): Unit = el.style.whiteSpace = value

  def setImageSource(uri: String): Unit = el.setAttribute("src", uri)

  private var rotation: Double = 0.0

  private def px(value: String): Double =
    value.stripSuffix("px").toDoubleOption.getOrElse(0.0)

/** Backend node factories. */
object Nodes:
  /** The page's mount point. */
  def root(): RenderNode =
    val existing = dom.document.getElementById("app")
    val app =
      if existing != null then existing.asInstanceOf[html.Element]
      else
        val created = dom.document.createElement("div").asInstanceOf[html.Element]
        created.id = "app"
        dom.document.body.appendChild(created)
        created
    app.style.position = "relative"
    // The render root is a fixed VIEWPORT on every other backend — SceneGraph
    // clips to the screen and there is no document to grow. The DOM is the only
    // one that reflows, so a node animating past an edge (an elastic curve
    // overshooting to x = -3, a row travelling right) grows the page and the
    // browser shows a scrollbar. That is not just cosmetic: the scrollbar
    // narrows the layout viewport, which shifts every subsequent measurement.
    // Clip, and the DOM behaves like the other backends.
    app.style.overflow = "hidden"
    app.style.width = "100%"
    app.style.height = "100%"
    new RenderNode(app)

  /** A container — an absolutely positioned `<div>` other nodes are appended to. */
  def container(): RenderNode =
    val el = dom.document.createElement("div").asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    new RenderNode(el)

  /** A text node — an absolutely positioned `<div>`. */
  def text(): RenderNode =
    val el = dom.document.createElement("div").asInstanceOf[html.Element]
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.style.font = "600 32px system-ui, sans-serif"
    el.style.color = "#1c1b1f"
    el.style.whiteSpace = "nowrap"
    new RenderNode(el)

  /** A Material Icons glyph, by codepoint, `size` pixels — youi's
    * `<i class="material-icons">`. The face's ascent is its whole em, so the
    * line box is the glyph's `size` square, as youi's `line-height: 1` has it. */
  def icon(glyph: String, size: Double): RenderNode =
    val node = text()
    node.setFont("Material Icons", 400, size)
    node.setText(glyph)
    node

  /** An image node — an `<img>`. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    val el = dom.document.createElement("img").asInstanceOf[html.Image]
    el.src = uri
    el.style.position = "absolute"
    el.style.left = "0px"
    el.style.top = "0px"
    el.width = width.toInt
    el.height = height.toInt
    new RenderNode(el)

/** The handful of non-render platform services the slice needs. */
object Platform:
  def log(message: String): Unit = dom.console.log("[ui] " + message)

  /** A failure with its cause: the console gets the stack. */
  def logError(message: String, t: Throwable): Unit =
    dom.console.log("[ui] ERROR " + message + ": " + t.toString)
    dom.console.error(t.asInstanceOf[js.Any])

  def ticks(): Double = dom.window.performance.now()

  /** Served next to index.html. */
  def imagePath(name: String): String = "images/" + name

  // The window's inner size, as youi's `ui` measures it. The root clips its
  // content, so no scrollbar ever takes space out of it.
  /** The DOM scrolls a scrollable node itself. */
  def nativeScroll(): Boolean = true
  /** `url` in a new tab. */
  def openUrl(url: String): Unit = dom.window.open(url, "_blank")
  /** `text` onto the clipboard: the async clipboard where the page is a secure context, else the selection command. */
  def copyText(text: String): Unit =
    val clipboard = dom.window.navigator.asInstanceOf[js.Dynamic].clipboard
    if !js.isUndefined(clipboard) then clipboard.writeText(text)
    else
      val area = dom.document.createElement("textarea").asInstanceOf[html.TextArea]
      area.value = text
      dom.document.body.appendChild(area)
      area.select()
      dom.document.execCommand("copy")
      dom.document.body.removeChild(area)

  /** The page's path and query (`/runs?state=x`) — what a screen is reached at. */
  def path(): String = dom.window.location.pathname + dom.window.location.search
  /** Show `path` in the address bar as a new history entry, without loading anything. */
  def pushPath(path: String): Unit = dom.window.history.pushState(null, "", path)
  /** The path after the browser's back or forward. */
  def onPath(f: String => Unit): Unit = dom.window.addEventListener("popstate", (_: dom.Event) => f(path()))

  def screenWidth(): Double = dom.window.innerWidth.toDouble
  def screenHeight(): Double = dom.window.innerHeight.toDouble

  /** `?freeze=<seconds>` in the page URL: step the animation that far on a virtual clock and stop (tools/check.sh).
    * Negative when absent. */
  def freezeSeconds(): Double =
    val value = new dom.URLSearchParams(dom.window.location.search).get("freeze")
    if value == null then -1.0 else value.toDoubleOption.getOrElse(-1.0)

  /** The frozen frame is complete. (tools/check.sh screenshots the page itself.) */
  def frozen(): Unit = ()

  /** `?name=value` in the page URL, or "" — how a launch chooses a demo (`demo`) and hands it a URL (`socket`). */
  def launchArg(name: String): String =
    val value = new dom.URLSearchParams(dom.window.location.search).get(name)
    if value == null then "" else value

  /** A page has nowhere to exit to; a demo that is done just stops. */
  def quit(): Unit = ()

  /** `millis` since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
  def localDateTime(millis: Long): String =
    val d = new js.Date(millis.toDouble)
    def two(n: Int): String = if n < 10 then "0" + n.toString else n.toString
    d.getFullYear().toInt.toString + "-" + two(d.getMonth().toInt + 1) + "-" + two(d.getDate().toInt) + " " +
      two(d.getHours().toInt) + ":" + two(d.getMinutes().toInt) + ":" + two(d.getSeconds().toInt)

  /** The WebSocket origin of the page itself (`ws://host:port`), so an app served by its own server needs no address. */
  def webSocketOrigin(): String =
    val l = dom.window.location
    (if l.protocol == "https:" then "wss://" else "ws://") + l.host

  /** Called whenever the window changes size. */
  /** Every key on the page, by its DOM name, pressed or released. */
  def onKey(f: (String, Boolean) => Unit): Unit =
    dom.document.addEventListener("keydown", (e: dom.KeyboardEvent) => f(e.key, true))
    dom.document.addEventListener("keyup", (e: dom.KeyboardEvent) => f(e.key, false))

  def onResize(f: () => Unit): Unit = dom.window.addEventListener("resize", (_: dom.Event) => f())

  // The same four the Roku backend reaches through BrightScript intrinsics.
  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  /** A site-absolute asset path (`/images/icon.png`), made relative to the page so the bundle works wherever
    * it is served. */
  def resolvePath(path: String): String = path.stripPrefix("/")

  /** Load `uri` and report its natural size. */
  def loadImage(uri: String, loaded: (Double, Double) => Unit): Unit =
    val img = dom.document.createElement("img").asInstanceOf[html.Image]
    img.addEventListener("load", (_: dom.Event) => loaded(img.naturalWidth.toDouble, img.naturalHeight.toDouble))
    img.src = uri

  /** Call `loaded` once a bundled face (index.html's `@font-face`) is usable. */
  def loadFont(family: String, weight: Int, loaded: () => Unit): Unit =
    val done: js.Function1[js.Any, Unit] = _ => loaded()
    dom.document.asInstanceOf[js.Dynamic].fonts
      .load(weight.toString + " 24px '" + family + "'")
      .applyDynamic("then")(done, done)

  /** Run `f` once the bundled faces have loaded, so text measures with its
    * real font rather than a fallback. (The Roku loads fonts synchronously.) */
  def whenFontsReady(f: () => Unit): Unit =
    val fonts = dom.document.asInstanceOf[js.Dynamic].fonts
    val loads = js.Array("400 24px 'Open Sans'", "600 24px 'Open Sans'", "400 24px 'Material Icons'")
      .map(face => fonts.load(face))
    val done: js.Function1[js.Any, Unit] = _ => f()
    js.Dynamic.global.Promise.all(loads).applyDynamic("then")(done, done)

  def animate(tick: () => Unit): Unit =
    lazy val frame: Double => Unit = _ =>
      tick()
      dom.window.requestAnimationFrame(frame)
    dom.window.requestAnimationFrame(frame)
