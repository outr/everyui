package everyui.markdown

/** How a `MarkdownView` sets its text: the body's size in points, code's, the headings' sizes as multiples of the
  * body's (`#` first), the gap between blocks, how far a list's items stand in, a table cell's padding, and the
  * tallest a picture is drawn. Colours come from the theme (`MarkdownView` names them). */
case class MarkdownStyle(points: Double = 15.0,
                         codePoints: Double = 13.0,
                         headings: List[Double] = List(1.6, 1.36, 1.18, 1.06, 1.0, 0.92),
                         spacing: Double = 12.0,
                         indent: Double = 24.0,
                         cellPadding: Double = 8.0,
                         imageHeight: Double = 480.0) {
  /** The size of a heading of `level`, 1 to 6. */
  def heading(level: Int): Double = points * headings(math.max(0, math.min(headings.size - 1, level - 1)))
}

object MarkdownStyle {
  /** Smaller, for a chat's bubbles. */
  lazy val compact: MarkdownStyle = MarkdownStyle(points = 14.0, codePoints = 12.5, spacing = 9.0, indent = 20.0,
    cellPadding = 6.0)
}
