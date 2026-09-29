package everyui.markdown

/** A document's lines (`Markdown.lines`), and where each starts in its text. */
case class MdLines(lines: Vector[String], starts: Vector[Int])
