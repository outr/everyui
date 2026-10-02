package everyui.render

import everyui.font.Face
import org.scalajs.dom

/** The faces the page's build declared (sbt-everyui's `everyuiWebFaces`), as it wrote them beside its `@font-face`
  * rules:
  *
  * {{{
  *   <meta name="everyui-fonts" content="normal 400 Open Sans; normal 400 Material Icons" data-later="normal 400 Roboto Mono">
  * }}}
  *
  * those waited for before the first frame, and those fetched only once words are drawn in them. A page that writes its
  * own rules has no such meta, and the bundled faces are waited for as they always were. */
object PageFaces {
  private lazy val meta: dom.Element = dom.document.querySelector("meta[name=everyui-fonts]")

  /** Whether the build wrote the page's faces. */
  def declared: Boolean = meta != null

  /** The faces to wait for at the start, as `document.fonts.load` takes them (`normal 400 24px 'Open Sans'`). */
  def start: List[String] = list("content").map { face =>
    val parts = face.split(" ", 3)
    parts(0) + " " + parts(1) + " 24px '" + parts(2) + "'"
  }

  /** Whether the build declared `face`, so the page's rules have it and it is not to be added again. */
  def has(face: Face): Boolean = declared && {
    val named = (if (face.italic) "italic " else "normal ") + face.weight.toString + " " + face.family
    list("content").contains(named) || list("data-later").contains(named)
  }

  /** Whether a `unicode-range` (`U+0-FF, U+131, U+4??`) holds `c`. */
  def covers(range: String, c: Int): Boolean = FaceWait.holds(range, new String(Character.toChars(c)))

  private def list(attribute: String): List[String] =
    if (meta == null) Nil
    else Option(meta.getAttribute(attribute)).getOrElse("").split(";").toList.map(_.trim).filter(_.nonEmpty)
}
