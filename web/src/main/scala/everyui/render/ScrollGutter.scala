package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** A scroll's own scrollbar kept off the layout. A browser puts a scroll's scrollbar at its end, which in a
  * right-to-left page is its left, and places what it holds from the scrollbar's inner edge: everything in it sat the
  * scrollbar's width right of where the layout put it, and a pointer, found by the layout, was that far off (a slider's
  * thumb a drag away from the finger). What it holds is moved back by the scrollbar's width, so it is where the layout
  * says, the scrollbar drawn over its edge as it is over the right edge left to right. */
object ScrollGutter {
  private var styled = false

  /** Keep `el`'s children where the layout puts them whichever side its scrollbar is on, as the scrollbar comes and
    * goes. */
  def watch(el: html.Element): Unit = {
    if (!styled) {
      styled = true
      val style = dom.document.createElement("style")
      style.textContent = "html[dir=rtl] .everyui-scroll:not(.everyui-ltr) > * { margin-left: calc(-1 * " +
        "var(--everyui-gutter, 0px)); }"
      dom.document.head.appendChild(style)
    }
    el.classList.add("everyui-scroll")
    val observer = js.Dynamic.global.ResizeObserver
    // the box inside the scrollbar narrows as one appears, and widens as it goes
    if (!js.isUndefined(observer)) {
      val measure: js.Function1[js.Any, Unit] = _ => gutter(el)
      js.Dynamic.newInstance(observer)(measure).observe(el)
    }
  }

  /** The scrollbar's width measured again now: what the box is wider than the room inside it, besides its edges (0
    * where it has none, as a scroll without its bar has). */
  def measure(el: html.Element): Unit = if (el.classList.contains("everyui-scroll")) gutter(el)

  // the scrollbar's width: what the box is wider than the room inside it, besides its edges
  private def gutter(el: html.Element): Unit = {
    val style = dom.window.getComputedStyle(el)
    val edges = style.borderLeftWidth.stripSuffix("px").toDoubleOption.getOrElse(0.0) +
      style.borderRightWidth.stripSuffix("px").toDoubleOption.getOrElse(0.0)
    val width = math.max(0.0, el.offsetWidth - el.clientWidth - edges)
    el.style.setProperty("--everyui-gutter", width.toString + "px")
  }
}
