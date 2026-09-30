package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

/** A wheel's turn in pixels, whatever the browser counted it in (`WheelEvent.deltaMode`): Chromium and Safari count
  * pixels, but Firefox counts a mouse wheel's notches in lines (3 a notch), and a page at a time where that is set, so
  * a list scrolled by the wheel moved three pixels a notch there. A line is the page's line of text (its text size, 16
  * pixels unless it sets its own, spaced as `line-height: normal` spaces it, 1.2 of it), as Firefox scrolls a page of
  * its own by, and a page is what the node shows, or the window where it shows nothing. */
object WheelDelta {
  /** The turn across and down, in pixels, as `f` takes it. */
  def apply(e: dom.WheelEvent, el: html.Element, f: (Double, Double) => Unit): Unit = e.deltaMode match {
    case 1 =>
      val line = lineHeight()
      f(e.deltaX * line, e.deltaY * line)
    case 2 =>
      f(e.deltaX * page(el.clientWidth, dom.window.innerWidth),
        e.deltaY * page(el.clientHeight, dom.window.innerHeight))
    case _ => f(e.deltaX, e.deltaY)
  }

  private def lineHeight(): Double =
    dom.window.getComputedStyle(dom.document.documentElement).fontSize.stripSuffix("px").toDoubleOption
      .filter(_ > 0.0).getOrElse(16.0) * 1.2

  private def page(own: Double, window: Double): Double = if (own > 0.0) own else window
}
