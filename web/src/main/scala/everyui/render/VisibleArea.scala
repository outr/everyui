package everyui.render

import everyui.component.{Component, ScrollContainer}
import everyui.focus.Focus
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** The part of the window the app is laid out in: the window less what a phone's on-screen keyboard covers. A phone's
  * browser puts its keyboard over the page rather than making the window shorter (iOS Safari always, Chrome on Android
  * unless the page asks otherwise), and says only in its visual viewport (`window.visualViewport`) how much of the
  * page still shows. So the app is as tall as that: what sits at its bottom (a chat's composer) stays just above the
  * keyboard as it opens, and comes back down as it closes.
  *
  * The browser also moves the page to show the field being written in, out from under the keyboard, which with the app
  * already short enough only leaves it shifted up (and, on the keyboard closing, a gap below it); the page is put back
  * at its top whenever it moves, unless the person has zoomed in with two fingers, which is theirs to move. And as the
  * area shrinks with a field being written in, that field is brought into view in whatever scrolls it, as it is when
  * the focus moves to it from the keys. */
private[render] object VisibleArea {
  private var installed = false
  private var last = 0.0

  // the browser's visual viewport, where it has one
  private def visual: Option[js.Dynamic] = {
    val v = dom.window.asInstanceOf[js.Dynamic].visualViewport
    if (js.isUndefined(v) || v == null) None else Some(v)
  }

  /** How tall the area the page shows is, in CSS pixels: the window's height, or less while a keyboard covers some. */
  def height(): Double = {
    val inner = dom.window.innerHeight.toDouble
    visual.map { v =>
      // zoomed in, the visual viewport is the window over the zoom; that much of the window is still the app's
      val shows = v.height.asInstanceOf[Double] * zoom(v)
      if (shows > 0.0 && shows < inner - 0.5) shows else inner
    }.getOrElse(inner)
  }

  private def zoom(v: js.Dynamic): Double = {
    val s = v.scale.asInstanceOf[js.UndefOr[Double]].getOrElse(1.0)
    if (s > 0.0) s else 1.0
  }

  /** `f` told whenever the area changes: the window resized, a keyboard opened or closed. */
  def onChange(f: () => Unit): Unit = {
    install()
    dom.window.addEventListener("resize", (_: dom.Event) => f())
    visual.foreach { v =>
      v.addEventListener("resize", ((_: dom.Event) => f()): js.Function1[dom.Event, Unit])
    }
  }

  private def install(): Unit = if (!installed) {
    installed = true
    last = height()
    dom.window.addEventListener("resize", (_: dom.Event) => changed())
    dom.window.addEventListener("scroll", (_: dom.Event) => home())
    visual.foreach { v =>
      v.addEventListener("resize", ((_: dom.Event) => changed()): js.Function1[dom.Event, Unit])
      v.addEventListener("scroll", ((_: dom.Event) => home()): js.Function1[dom.Event, Unit])
    }
  }

  private def changed(): Unit = {
    val now = height()
    val shrank = now < last - 0.5
    last = now
    home()
    // once the app is laid out at its new height, which its listeners above do as they hear the same change
    if (shrank) dom.window.setTimeout(() => reveal(), 0)
  }

  // the page back at its top, where the app's pointer places are measured from, unless zoomed in with two fingers
  private def home(): Unit = {
    val panned = visual.exists { v =>
      zoom(v) <= 1.001 && (v.offsetTop.asInstanceOf[Double] != 0.0 || v.offsetLeft.asInstanceOf[Double] != 0.0)
    }
    if (dom.window.scrollX != 0.0 || dom.window.scrollY != 0.0 || panned) dom.window.scrollTo(0, 0)
  }

  // the field being written in, brought into view in each scroll it is in, the nearest first
  private def reveal(): Unit = {
    val active = dom.document.activeElement
    if (writable(active)) Focus.focused().filter(c => c.node.el.contains(active)).foreach { c =>
      var at: Option[Component] = c.parent()
      while (at.nonEmpty) {
        at.get match {
          case s: ScrollContainer => s.reveal(c)
          case _ => ()
        }
        at = at.flatMap(_.parent())
      }
    }
  }

  // whether `el` is somewhere a keyboard writes: a field, a text box, or editable text
  private def writable(el: dom.Element): Boolean = el match {
    case input: html.Input => !VisibleArea.unwritten.contains(input.`type`.toLowerCase)
    case _: html.TextArea => true
    case h: html.Element => h.isContentEditable
    case _ => false
  }
  private val unwritten = Set("button", "checkbox", "radio", "range", "color", "file", "submit", "reset", "image",
    "hidden")
}
