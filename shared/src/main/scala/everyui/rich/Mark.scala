package everyui.rich

/** How a stretch of a rich document's words is styled: bold, italic, underlined, struck through, set as code, or a
  * link. A run holds any of them at once, but one link at most. */
sealed trait Mark

object Mark {
  case object Bold extends Mark
  case object Italic extends Mark
  case object Underline extends Mark
  case object Strike extends Mark
  /** Inline code: in a monospaced face, and nothing else on it is written into Markdown. */
  case object Code extends Mark
  /** A link to `href`. */
  case class Link(href: String) extends Mark

  /** The marks a toolbar toggles, in the order Markdown writes them round words (outermost first). */
  val toggled: List[Mark] = List(Bold, Italic, Underline, Strike, Code)

  /** The link among `marks`, if there is one. */
  def link(marks: Set[Mark]): Option[String] = marks.collectFirst { case Link(href) => href }
}
