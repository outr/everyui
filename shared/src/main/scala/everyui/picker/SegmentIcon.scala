package everyui.picker

import everyui.component.{Component, MaterialIconView}
import everyui.draw.{VectorArt, VectorIcon}
import everyui.font.MaterialIcon

/** A mark before a segment chip's words: a Material icon, or a monochrome SVG (a provider's logo, a kind of file), from
  * a file or from its path data. Every one is drawn in the chip's own colour for its words, so it follows the theme,
  * the chip chosen and the focus; the colours of an SVG are not its own.
  *
  * {{{
  *   Segment("local", "This machine", Some(SegmentIcon(MaterialIcon.Computer)))
  *   Segment("acme", "Acme", Some(SegmentIcon.Url("/providers/acme.svg")))
  *   Segment("shapes", "Shapes", Some(SegmentIcon.Path("M12 2 2 22h20Z")))
  * }}}
  *
  * A Material icon shows wherever fonts do. An SVG shows on the web, the desktop and Android; a Roku and an Apple TV draw
  * no paths, and there a chip with one shows its words alone. */
sealed trait SegmentIcon {
  /** Its view, `points` pixels square, coloured by whoever shows it; None where this platform cannot draw it. */
  def view(points: Double): Option[Component]
}

object SegmentIcon {
  def apply(icon: MaterialIcon): SegmentIcon = Material(icon)

  /** A glyph of the bundled Material Icons. */
  case class Material(icon: MaterialIcon) extends SegmentIcon {
    def view(points: Double): Option[Component] = Some(new MaterialIconView(icon, points))
  }

  /** A monochrome SVG file (`VectorIcon.load` says where it may be, and `VectorArt.parse` what of it is drawn): the
    * chip shows its words at once, and the mark beside them once the file has come. */
  case class Url(url: String) extends SegmentIcon {
    def view(points: Double): Option[Component] = if (VectorIcon.drawn) Some(VectorIcon.load(url, points)) else None
  }

  /** SVG path data (a `<path>`'s `d`), filled, in a viewBox `width` by `height` from (`left`, `top`): 24 by 24 from
    * the corner, as most icons are, unless said. `evenOdd` fills it by the even-odd rule (SVG's `fill-rule`). */
  case class Path(d: String, width: Double = 24.0, height: Double = 24.0, left: Double = 0.0, top: Double = 0.0,
                  evenOdd: Boolean = false) extends SegmentIcon {
    def view(points: Double): Option[Component] =
      if (VectorIcon.drawn) Some(VectorIcon(VectorArt.path(d, width, height, left, top, evenOdd), points)) else None
  }
}
