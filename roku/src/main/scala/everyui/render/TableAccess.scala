package everyui.render

import everyui.component.Component
import everyui.markdown.{MarkdownContext, MdBlock, TableView}

/** Tables here are drawn as they are (`everyui.markdown.TableView`): a remote has no pointer to sort or choose rows with,
  * and there is no clipboard to copy them to or anywhere to save them, so `everyui.table.DataTable` is never made, and
  * the channel carries none of it. */
object TableAccess {
  /** Whether a Markdown table is a `DataTable` here: it is not. */
  def rich(): Boolean = false

  /** `table` drawn as it is. */
  def markdown(table: MdBlock.TableBlock, ctx: MarkdownContext, width: => Double): Component = new TableView(table, ctx, width)

  /** A table drawn as it is takes nothing in place: it is drawn again. */
  def update(view: Component, now: MdBlock.TableBlock): Boolean = false

  /** `a` against `b`, letter by letter. */
  def collate(a: String, b: String): Int = a.compareTo(b)
}
