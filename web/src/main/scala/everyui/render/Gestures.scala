package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** Pinches (`everyui.zoom.ZoomCanvas`): two fingers on a touch screen, followed by their pointers; a trackpad's pinch
  * comes as the wheel with Ctrl held, which the canvas zooms by itself. */
object Gestures {
  /** Whether a pinch can come: a trackpad's does on any browser, and two fingers where there is a touch screen. */
  def pinches(): Boolean = true

  /** `node`'s gestures its own: two fingers pinching are told to `pinch` as they move (how much further apart since
    * the last, and the point between them, in the app's units), and `ended` when one lifts; and the wheel over
    * it is kept from the page, which would otherwise scroll what it is in, or zoom the page on Ctrl. Heard before the
    * node's pointer events, so a canvas knows a finger's move is part of a pinch before it takes it for a drag. */
  def take(node: RenderNode, pinch: (Double, Double, Double) => Unit, ended: () => Unit): Unit = {
    val el = node.el
    el.style.setProperty("touch-action", "none")
    val kept = js.Dynamic.literal(passive = false).asInstanceOf[dom.EventListenerOptions]
    el.addEventListener("wheel", (e: dom.WheelEvent) => {
      e.preventDefault()
      e.stopPropagation()
    }, kept)
    var fingers = Map.empty[Double, (Double, Double)]
    var apart = 0.0
    def between(): (Double, Double, Double) = {
      val two = fingers.values.take(2).toList
      val (ax, ay) = two.head
      val (bx, by) = two(1)
      ((ax + bx) / 2.0, (ay + by) / 2.0, math.hypot(bx - ax, by - ay))
    }
    def touch(e: dom.PointerEvent): Boolean = e.pointerType == "touch"
    el.addEventListener("pointerdown", (e: dom.PointerEvent) => if (touch(e)) {
      fingers += e.pointerId.toDouble -> ((e.clientX / RenderNode.heardAt(e), e.clientY / RenderNode.heardAt(e)))
      if (fingers.size == 2) {
        val (x, y, d) = between()
        apart = d
        pinch(1.0, x, y)
      }
    })
    el.addEventListener("pointermove", (e: dom.PointerEvent) => if (touch(e) && fingers.contains(e.pointerId.toDouble)) {
      fingers += e.pointerId.toDouble -> ((e.clientX / RenderNode.heardAt(e), e.clientY / RenderNode.heardAt(e)))
      if (fingers.size >= 2) {
        val (x, y, d) = between()
        if (apart > 0.0 && d > 0.0) pinch(d / apart, x, y)
        apart = d
      }
    })
    val lift: js.Function1[dom.PointerEvent, Unit] = (e: dom.PointerEvent) =>
      if (touch(e) && fingers.contains(e.pointerId.toDouble)) {
        val were = fingers.size
        fingers -= e.pointerId.toDouble
        if (were >= 2 && fingers.size < 2) {
          apart = 0.0
          ended()
        }
      }
    el.addEventListener("pointerup", lift)
    el.addEventListener("pointercancel", lift)
  }

  /** `node` tapped by a finger from a little outside it where it is smaller than a fingertip (`everyui.event.TapSlop`),
    * or no longer. */
  def slop(node: RenderNode, on: Boolean): Unit =
    if (on) {
      node.el.setAttribute(TapReach.attribute, "")
      TapReach.install()
    }
    else node.el.removeAttribute(TapReach.attribute)

  /** `node` never the pointer's: a finger, a click and the wheel go through it to what is under it (a shade drawn over
    * a table). */
  def passThrough(node: RenderNode): Unit = node.el.style.setProperty("pointer-events", "none")

  /** `node` the pointer's only where one of its children is: a layer over the screen laid in pieces (a spotlight's dim
    * round its hole), whose box would otherwise take the pointer in the gaps between them. */
  def passBetween(node: RenderNode): Unit = {
    if (dom.document.getElementById("everyui-between") == null) {
      val rule = dom.document.createElement("style")
      rule.id = "everyui-between"
      rule.textContent = "[data-everyui-between] > * { pointer-events: auto; }"
      dom.document.head.appendChild(rule)
    }
    node.el.setAttribute("data-everyui-between", "")
    passThrough(node)
  }
}
