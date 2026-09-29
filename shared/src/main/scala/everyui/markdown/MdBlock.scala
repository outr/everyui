package everyui.markdown

import everyui.component.types.TextAlign

/** A block of a parsed Markdown document: a paragraph, a heading, code, a quote, a list, a table, a rule. */
sealed trait MdBlock

object MdBlock {
  case class Paragraph(content: List[MdInline]) extends MdBlock
  /** `# Title` to `###### Title`, or a line underlined with `===` (1) or `---` (2). */
  case class Heading(level: Int, content: List[MdInline]) extends MdBlock
  /** Fenced (with its `language`, the fence's first word) or indented code. `closed` is false for a fence that has
    * not ended yet, as while an answer streams in: the code so far. */
  case class CodeBlock(language: String, code: String, closed: Boolean = true) extends MdBlock
  /** `> ...`: blocks quoted. */
  case class Quote(blocks: List[MdBlock]) extends MdBlock
  /** A list, numbered from `start` when `ordered`; `tight` when its items are not set apart by blank lines. */
  case class BulletList(ordered: Boolean, start: Int, tight: Boolean, items: List[MdItem]) extends MdBlock
  /** `---`, `***` or `___`: a line across. */
  case object Rule extends MdBlock
  /** A table: its columns' alignment, its header's cells and its rows' cells. */
  case class TableBlock(align: List[TextAlign], header: List[List[MdInline]], rows: List[List[List[MdInline]]])
    extends MdBlock
  /** Raw HTML, which is shown as the text it is, never rendered. */
  case class Html(source: String) extends MdBlock
  /** Link reference definitions (`[label]: href "title"`), which show nothing: lower-cased labels to their links. */
  case class Definitions(links: List[MdReference]) extends MdBlock
}
