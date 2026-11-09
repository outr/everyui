package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** Whether the click the browser is about to send was a click at all. A press belongs to what it went down on, as a
  * desktop's and a phone's own controls have it: the innermost thing there that answers a click (`mark`ed) or takes
  * typing (a field). A browser instead sends its click to the innermost element holding both where the button went
  * down and where it came up, which made a click of a drag: one that selected words in a field in a dialog and ended
  * over the dim around it went to the dialog's sheet, as a click outside the dialog, and closed it. A mouse's click
  * here counts only when its press and its release landed on the same such thing; a click from a key (the browser's
  * own Enter or Space on a button, `detail` 0) and a finger's tap (judged where it went down, `TapReach`) are left as
  * they are. */
private[render] object PressOwner {
  private var installed = false
  private var pressed: Option[dom.Node] = None
  private var split = false
  // where a finger went down, and whether it has since gone further from there than a tap may
  private var touchX = 0.0
  private var touchY = 0.0
  private var strayed = false

  /** `el` answers clicks: a press on it, or on something in it that does not, is its. */
  def mark(el: html.Element): Unit = {
    el.asInstanceOf[js.Dynamic].updateDynamic("everyuiClicks")(true)
    install()
  }

  /** Whether `e`, a click, came of a press and a release on different things, or of a finger that strayed (`swiped`):
    * not to be heard. */
  def splits(e: dom.MouseEvent): Boolean = (split || strayed) && e.isTrusted && e.detail > 0

  /** Whether the finger last put down went further than a tap may before it lifted: a scroll or a swipe, which clicks
    * nothing, as a phone's own lists have it, wherever a browser would still make a click of it. */
  def swiped: Boolean = strayed

  private def install(): Unit = if (!installed) {
    installed = true
    val capture = js.Dynamic.literal(capture = true).asInstanceOf[dom.EventListenerOptions]
    dom.window.addEventListener("pointerdown", (e: dom.PointerEvent) => if (e.button == 0) {
      split = false
      strayed = false
      pressed = if (e.pointerType == "touch") None else owner(e.target)
      touchX = e.clientX
      touchY = e.clientY
    }, capture)
    dom.window.addEventListener("pointermove", (e: dom.PointerEvent) => if (e.pointerType == "touch" && !strayed)
      strayed = everyui.event.ScrollPress.beyond(touchX, touchY, e.clientX, e.clientY), capture)
    dom.window.addEventListener("pointerup", (e: dom.PointerEvent) => if (e.button == 0) {
      // where it came up, not the element holding a captured pointer, which the release is sent to
      val released = Option(dom.document.elementFromPoint(e.clientX, e.clientY)).flatMap(owner)
      split = pressed.exists(p => p.isConnected && released.exists(r => !(r eq p)))
      pressed = None
    }, capture)
    // heard by the window last, once everything under the pointer has had the click
    dom.window.addEventListener("click", (_: dom.MouseEvent) => {
      split = false
      strayed = false
    })
  }

  // the innermost thing from `target` up that answers a click or takes typing
  private def owner(target: dom.EventTarget): Option[dom.Node] = {
    var at = target.asInstanceOf[dom.Node]
    while (at != null && !answers(at)) at = at.parentNode
    Option(at)
  }

  private def answers(node: dom.Node): Boolean = node match {
    case h: html.Element => !js.isUndefined(h.asInstanceOf[js.Dynamic].everyuiClicks) || h.contentEditable == "true" ||
      h.contentEditable == "plaintext-only" || h.tagName == "INPUT" || h.tagName == "TEXTAREA" || h.tagName == "SELECT"
    case _ => false
  }
}
