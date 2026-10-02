package everyui.render

import everyui.event.TapSlop
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** A finger's tap near a small control taken to it (`everyui.event.TapSlop`): a click the browser made of a touch,
  * landing on nothing that answers a click itself, goes to the nearest control marked `attribute` whose reach holds
  * it, as a click just inside its edge. What the tap landed on that answers a click (a field, a row, a button) keeps
  * it, unless it holds that control, as a row holds its menu's glyph. A control under a dialog, or scrolled out of its
  * list, is not reached, as its centre is not on the page to be tapped. A mouse's and a pen's clicks are left alone.
  * Chrome on a phone moves a tap a little towards what answers clicks first (its touch adjustment); this reaches
  * further, and the same on every browser. */
private[render] object TapReach {
  val attribute: String = "data-everyui-slop"
  private var installed = false
  // whether the press the click comes of was a finger's
  private var finger = false

  def install(): Unit = if (!installed) {
    installed = true
    val capture = js.Dynamic.literal(capture = true).asInstanceOf[dom.EventListenerOptions]
    dom.window.addEventListener("pointerdown", (e: dom.PointerEvent) => finger = e.pointerType == "touch", capture)
    dom.window.addEventListener("click", (e: dom.MouseEvent) => if (finger && e.isTrusted) retarget(e), capture)
  }

  private def retarget(e: dom.MouseEvent): Unit = e.target match {
    case target: dom.Element =>
      val x = e.clientX
      val y = e.clientY
      val controls = answering(target)
      val marked = dom.document.querySelectorAll("[" + attribute + "]")
      val near = (0 until marked.length).toVector.map(i => marked(i)).collect { case h: html.Element => h }
        .filter(c => controls.forall(_.contains(c)) && !c.contains(target))
        .filter(c => TapSlop.pick(x, y, Vector(box(c))).nonEmpty)
        .filter(reachable)
      TapSlop.pick(x, y, near.map(box)).map(near).foreach { c =>
        val r = c.getBoundingClientRect()
        e.stopPropagation()
        e.preventDefault()
        val init = js.Dynamic.literal(bubbles = true, cancelable = true, composed = true, view = dom.window,
          clientX = math.max(r.left + 1.0, math.min(r.right - 1.0, x)),
          clientY = math.max(r.top + 1.0, math.min(r.bottom - 1.0, y)),
          screenX = e.screenX, screenY = e.screenY, button = 0, detail = 1, pointerType = "touch",
          isPrimary = true)
        val kind = if (js.isUndefined(js.Dynamic.global.PointerEvent)) js.Dynamic.global.MouseEvent
          else js.Dynamic.global.PointerEvent
        c.dispatchEvent(js.Dynamic.newInstance(kind)("click", init).asInstanceOf[dom.Event])
      }
    case _ => ()
  }

  // what answers a click from `target` up: a control of the app's, a link, a field
  private def answering(target: dom.Element): List[dom.Element] = {
    var out: List[dom.Element] = Nil
    var at: dom.Node = target
    while (at != null && at != dom.document.body) {
      at match {
        case h: html.Element if h.hasAttribute(attribute) || h.style.cursor == "pointer" || h.isContentEditable ||
          TapReach.fields.contains(h.tagName) => out = h :: out
        case _ => ()
      }
      at = at.parentNode
    }
    out
  }
  private val fields = Set("A", "BUTTON", "INPUT", "SELECT", "TEXTAREA", "VIDEO", "IFRAME")

  private def box(c: html.Element): (Double, Double, Double, Double) = {
    val r = c.getBoundingClientRect()
    (r.left, r.top, r.width, r.height)
  }

  // whether a finger could tap `c` itself: its centre on the page and not under anything else
  private def reachable(c: html.Element): Boolean = {
    val r = c.getBoundingClientRect()
    val top = dom.document.elementFromPoint(r.left + r.width / 2.0, r.top + r.height / 2.0)
    top != null && c.contains(top)
  }
}
