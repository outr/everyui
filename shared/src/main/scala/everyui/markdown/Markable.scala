package everyui.markdown

import everyui.component.Component

/** A block of a `MarkdownView` that passages can be marked in (`MarkdownView.passages`): a paragraph or a heading by
  * its lines, a table by its cells, row by row. Code is none. */
private[markdown] trait Markable {
  /** The words of each of its units (a line, a cell), as they are drawn, in the order they read. */
  def markWords: Vector[String]

  /** Its units drawn with `spans` marked in each, one for each unit `markWords` gave: none marks nothing. */
  def mark(spans: Vector[Vector[MarkSpan]]): Unit

  /** What draws unit `i`, to be brought into view. */
  def markUnit(i: Int): Component
}
