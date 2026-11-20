package everyui.render

import org.scalajs.dom

/** A list that scrolls itself (`RenderNode.setTouchScrolls`) keeps a finger's drag up or down that begins on a scroll
  * of the page's own inside it: a table's rows, a table's or a code block's rail. The browser reads `touch-action` only
  * as far up as the nearest element that scrolls, so the list's `pan-x` never reached a drag begun in one, and the
  * browser took it for a pan of its own a few pixels in (pointercancel), which scrolled nothing, and the list heard no
  * more of it: a chat could not be dragged by its tables. The first move further than a tap may (`DragAxis`, as the
  * list settles its own drag) that goes more up or down than across is kept from the browser, unless a scroll between
  * where it began and the list can still go that way, which the browser then scrolls, as Android leaves such a drag
  * (`NodeGroup.scrollUnder`); one more across than up or down is left to the browser, a rail's. */
object TouchScrolls {
  /** `list`'s drags up and down kept from the browser where nothing in it under the finger scrolls that way: once, and
    * only where there is a touch screen to drag on (`Platform.touchSupport`). */
  def keep(list: dom.Element): Unit = if (Platform.touchSupport() && !list.hasAttribute("data-everyui-keeps")) {
    list.setAttribute("data-everyui-keeps", "")
    var fromX = 0.0
    var fromY = 0.0
    // whether the touch's way is settled, and whether it is kept from the browser: every move of it, as any move let
    // through may start the browser's pan
    var settled = true
    var kept = false
    list.addEventListener("touchstart", (e: dom.TouchEvent) => {
      settled = e.touches.length != 1
      kept = false
      if (!settled) {
        fromX = e.touches(0).clientX
        fromY = e.touches(0).clientY
      }
    }, WebFeatures.listening(capture = false, passive = true))
    list.addEventListener("touchmove", (e: dom.TouchEvent) => {
      if (!settled && e.touches.length == 1) {
        val dy = e.touches(0).clientY - fromY
        val way = everyui.event.DragAxis.settle(e.touches(0).clientX - fromX, dy, vertical = true,
          everyui.event.ScrollPress.slop)
        if (way != 0) {
          settled = true
          kept = way > 0 && !scrolls(e.target, list, if (dy < 0) 1 else -1)
        }
      }
      if (kept && e.cancelable) e.preventDefault()
    }, WebFeatures.listening(capture = false, passive = false))
  }

  // whether an element from `at` up to `list` scrolls up and down and can still go `toward` (1 on, -1 back)
  private def scrolls(at: dom.EventTarget, list: dom.Element, toward: Int): Boolean = {
    var el: dom.Element = at match {
      case e: dom.Element => e
      case _ => null
    }
    var can = false
    while (!can && el != null && !(el eq list)) {
      val overflow = dom.window.getComputedStyle(el).overflowY
      if ((overflow == "auto" || overflow == "scroll") && el.scrollHeight > el.clientHeight)
        can = everyui.event.DragAxis.goes(el.scrollTop, el.scrollHeight - el.clientHeight, toward)
      el = el.parentNode match {
        case p: dom.Element => p
        case _ => null
      }
    }
    can
  }
}
