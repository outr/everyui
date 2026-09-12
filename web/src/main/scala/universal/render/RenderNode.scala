package universal.render

import org.scalajs.dom
import org.scalajs.dom.html

/** **The one invention.** The abstract render node, DOM edition.
  *
  * Signature-for-signature the same as its Roku twin
  * (`roku/.../universal/render/RenderNode.scala`) — that parallel IS the seam.
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

  def append(child: RenderNode): Unit = el.appendChild(child.el)

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

  def ticks(): Double = dom.window.performance.now()

  /** Served next to index.html. */
  def imagePath(name: String): String = "images/" + name

  // `documentElement.clientWidth`, not `window.innerWidth`: innerWidth INCLUDES
  // a classic scrollbar, so it reports space the app cannot actually draw in.
  // clientWidth is the real layout viewport.
  def screenWidth(): Double = dom.document.documentElement.clientWidth.toDouble
  def screenHeight(): Double = dom.document.documentElement.clientHeight.toDouble

  // The same four the Roku backend reaches through BrightScript intrinsics.
  def sqrt(x: Double): Double = math.sqrt(x)
  def pow(x: Double, y: Double): Double = math.pow(x, y)
  def sin(x: Double): Double = math.sin(x)
  def cos(x: Double): Double = math.cos(x)

  def animate(tick: () => Unit): Unit =
    lazy val frame: Double => Unit = _ =>
      tick()
      dom.window.requestAnimationFrame(frame)
    dom.window.requestAnimationFrame(frame)
