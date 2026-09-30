package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** Words cut short to fit (`TextView.maxWidth`, `maxLines`) on the page. The cut is made by measuring the words,
  * which a page can do only once they are on it and drawn in their own face: `ready` says whether they are, and calls
  * back once a face they wait for has loaded (a node put on the page is measured, and cut, then). Until the cut is made
  * the page holds the words to their room itself (`limit`), with an ellipsis on a single line. */
object TextFit {
  /** Whether `el`'s words measure as they will be drawn: on the page, in a face that has loaded. Where their face
    * has yet to load, `loaded` once it has. */
  def ready(el: html.Element, loaded: () => Unit): Boolean = el.isConnected && {
    val fonts = dom.document.asInstanceOf[js.Dynamic].fonts
    val waiting = !js.isUndefined(fonts) && waitsFor(el, fonts).nonEmpty
    if (waiting) {
      val style = el.style
      val font = List(style.fontStyle, style.fontWeight, style.fontSize, style.fontFamily).filter(_.nonEmpty)
        .mkString(" ")
      // called back only once nothing is waited for: a face that failed to load is not waited for again
      val done: js.Function1[js.Any, Unit] = _ => if (waitsFor(el, fonts).isEmpty) loaded()
      val failed: js.Function1[js.Any, Unit] = _ => ()
      fonts.load(font, if (el.textContent.isEmpty) " " else el.textContent).applyDynamic("then")(done, failed)
    }
    !waiting
  }

  // The faces of the words' own family (the first named: the others are fallbacks for what it cannot draw), in their
  // slant and weight (or any weight, where none is that one), that are loading, or not yet asked for and meant for any
  // letter. The browser's own `check` answers that a face still loading, or never asked for, is ready.
  private def waitsFor(el: html.Element, fonts: js.Dynamic): js.Array[js.Dynamic] = {
    val style = el.style
    val family = named(style.fontFamily.split(",").headOption.getOrElse(""))
    val slant = if (style.fontStyle.isEmpty) "normal" else style.fontStyle
    val weight = if (style.fontWeight.isEmpty) "400" else style.fontWeight
    val own = js.Array.from(fonts.asInstanceOf[js.Iterable[js.Dynamic]])
      .filter(f => named(f.family.asInstanceOf[String]) == family && f.style.asInstanceOf[String] == slant)
    val weighted = own.filter(_.weight.asInstanceOf[String] == weight)
    (if (weighted.isEmpty) own else weighted).filter { f =>
      val status = f.status.asInstanceOf[String]
      val everyLetter = f.unicodeRange.asInstanceOf[String].equalsIgnoreCase("U+0-10FFFF")
      status == "loading" || (status == "unloaded" && everyLetter)
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
