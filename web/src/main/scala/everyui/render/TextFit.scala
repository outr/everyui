package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** Words cut short to fit (`TextView.maxWidth`, `maxLines`) on the page. The cut is made by measuring the words,
  * which a page can do only once they are on it and drawn in their own face: `ready` says whether they are, and calls
  * back once a face they wait for has loaded (a node put on the page is measured, and cut, then). Until the cut is made
  * the page holds the words to their room itself (`limit`), with an ellipsis on a single line. */
object TextFit {
  /** Whether `el`'s words measure as they will be drawn: on the page, in faces that have loaded, its own and its runs'
    * (a paragraph's code, in a face of its own). Where a face has yet to load, it is asked for, and `loaded` once every
    * one has; asked again while they load, it waits on the same. */
  def ready(el: html.Element, loaded: () => Unit): Boolean = el.isConnected && {
    val fonts = dom.document.asInstanceOf[js.Dynamic].fonts
    val waiting = if (js.isUndefined(fonts)) Nil else faces(el).filter(f => waitsFor(f, fonts, el.textContent).nonEmpty)
    val held = el.asInstanceOf[js.Dynamic]
    if (waiting.nonEmpty && !js.isUndefined(held.everyuiFaces)) ()
    else if (waiting.nonEmpty) {
      held.everyuiFaces = true
      val text = if (el.textContent.isEmpty) " " else el.textContent
      val size = if (el.style.fontSize.isEmpty) "16px" else el.style.fontSize
      // once they are in (or failed, and are not waited for again), asked again: words changed meanwhile may wait for
      // another face
      val done: js.Function1[js.Any, Unit] = _ => {
        js.special.delete(held, "everyuiFaces")
        if (ready(el, loaded)) loaded()
      }
      val loads = js.Array(waiting.map(f => fonts.load(f._2 + " " + f._3 + " " + size + " '" + f._1 + "'",
        text))*)
      js.Dynamic.global.Promise.all(loads).applyDynamic("then")(done, done)
    }
    waiting.isEmpty
  }

  // The faces `el`'s words are drawn in: its own, and those of its runs that name another family, slant or weight.
  private def faces(el: html.Element): List[(String, String, String)] = {
    def of(style: dom.CSSStyleDeclaration, outer: (String, String, String)): (String, String, String) = (
      if (style.fontFamily.isEmpty) outer._1 else named(style.fontFamily.split(",").headOption.getOrElse("")),
      if (style.fontStyle.isEmpty) outer._2 else style.fontStyle,
      if (style.fontWeight.isEmpty) outer._3 else style.fontWeight)
    val own = of(el.style, ("", "normal", "400"))
    val runs = el.children
    (own :: (0 until runs.length).toList.map(i => of(runs(i).asInstanceOf[html.Element].style, own))).distinct
  }

  // The faces of `face`'s family (the first named: the others are fallbacks for what it cannot draw), in its slant and
  // weight (or any weight, where none is that one), that are loading, or not yet asked for and meant for any letter or
  // for one of `text`'s (a slice of a face by script, a build's). The browser's own `check` answers that a face still
  // loading, or never asked for, is ready.
  private def waitsFor(face: (String, String, String), fonts: js.Dynamic, text: String): js.Array[js.Dynamic] = {
    val own = js.Array.from(fonts.asInstanceOf[js.Iterable[js.Dynamic]])
      .filter(f => named(f.family.asInstanceOf[String]) == face._1 && f.style.asInstanceOf[String] == face._2)
    val weighted = own.filter(_.weight.asInstanceOf[String] == face._3)
    (if (weighted.isEmpty) own else weighted).filter { f =>
      val status = f.status.asInstanceOf[String]
      val range = f.unicodeRange.asInstanceOf[String]
      status == "loading" ||
        (status == "unloaded" && (range.equalsIgnoreCase("U+0-10FFFF") || PageFaces.holds(range, text)))
    }
  }

  // a family as it is named, whether quoted (Firefox quotes a FontFace's own) or not
  private def named(family: String): String =
    family.trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")

  /** Hold `el`'s words to `width` (0 for no limit), with an ellipsis where one line runs past it, and to `lines`
    * lines of its line height (0 for no limit). 0 and 0 lifts the limit, for the words to be measured whole. */
  def limit(el: html.Element, width: Double, lines: Int): Unit = {
    el.style.maxWidth = if (width > 0.0) width.toString + "px" else ""
    val line = el.style.lineHeight.stripSuffix("px").toDoubleOption.getOrElse(0.0)
    el.style.maxHeight = if (lines > 0 && line > 0.0) (line * lines).toString + "px" else ""
    if (width > 0.0 || lines > 0) {
      el.style.overflow = "hidden"
      el.style.textOverflow = "ellipsis"
    }
  }
}
