package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** What is under a still mouse, looked at again once something on the page moved, changed size, was shown, hidden,
  * put on or taken off: a browser says the pointer came onto something or went off it only as the pointer moves (or
  * what it was over is taken away), so a card a list moved up under a still mouse was not over until the mouse moved,
  * the tools it shows while over hidden and a click on them landing on the card. A look a frame, after the frame's
  * changes, through `document.elementFromPoint`, told to `over` alone (`watch`): `enter` and `leave` stay the pointer's
  * own doing, so what takes the focus as the pointer comes onto it (a menu's row) does not take it from the keys as
  * they scroll it under a still mouse. Where the pointer is is known only from a mouse or a pen: a finger leaves none.
  * The desktop needs none of this: JavaFX looks again at every pulse. */
private[render] object WebHover {
  private val Key = "everyuiOver"
  // the mouse's place on the page (CSS pixels from its top left), NaN while it is off it or unknown
  private var x = Double.NaN
  private var y = Double.NaN
  // what is told the mouse is over it, by the page or by a look
  private var lit = Set.empty[dom.Element]
  private var watching = false
  private var due = false

  /** Tell `told` whether the mouse is over `el` as what is under it moves, beside the browser's own enter and leave. */
  def watch(el: dom.Element, told: Boolean => Unit): Unit = {
    val tell: js.Function1[Boolean, Unit] = on => told(on)
    el.asInstanceOf[js.Dynamic].updateDynamic(Key)(tell)
    el.addEventListener("pointerenter", (_: dom.Event) => lit += el)
    el.addEventListener("pointerleave", (_: dom.Event) => lit -= el)
    if (!watching) {
      watching = true
      val at: js.Function1[dom.PointerEvent, Unit] = e =>
        if (e.pointerType == "touch") { x = Double.NaN; y = Double.NaN }
        else { x = e.clientX; y = e.clientY }
      val options = js.Dynamic.literal(capture = true, passive = true).asInstanceOf[dom.EventListenerOptions]
      dom.document.addEventListener("pointermove", at, options)
      dom.document.addEventListener("pointerdown", at, options)
      dom.document.addEventListener("pointerup", at, options)
      // off the page: nothing is under it
      dom.document.addEventListener("pointerout", (e: dom.PointerEvent) => if (e.relatedTarget == null) {
        x = Double.NaN
        y = Double.NaN
      }, options)
    }
  }

  /** Something on the page moved, changed size, showed, hid, came or went: a look at what is under the mouse after the
    * frame's changes, once however many there are. */
  def changed(): Unit = if (watching && !due && !x.isNaN) {
    due = true
    dom.window.requestAnimationFrame(_ => look())
  }

  private def look(): Unit = {
    due = false
    if (!x.isNaN) {
      var under = Set.empty[dom.Element]
      var at: dom.Element = dom.document.elementFromPoint(x, y)
      while (at != null) {
        if (!js.isUndefined(at.asInstanceOf[js.Dynamic].selectDynamic(Key))) under += at
        at = at.parentNode match {
          case p: dom.Element => p
          case _ => null
        }
      }
      val off = lit -- under
      val on = under -- lit
      lit = (lit -- off) ++ on
      off.foreach(tell(_, false))
      on.foreach(tell(_, true))
    }
  }

  private def tell(el: dom.Element, over: Boolean): Unit =
    el.asInstanceOf[js.Dynamic].selectDynamic(Key).asInstanceOf[js.Function1[Boolean, Unit]](over)
}
