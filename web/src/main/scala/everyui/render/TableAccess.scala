package everyui.render

import everyui.component.Component
import everyui.markdown.{MarkdownContext, MarkdownTable, MdBlock}
import scala.scalajs.js

/** Tables that sort, filter, copy and export (`everyui.table.DataTable`): every Markdown table is one here, and words
  * are ordered by the browser's collation for the app's language, numbers in words by their value ("item 2" before
  * "item 10"). */
object TableAccess {
  private var collator: js.Dynamic = null
  private var collating = ""

  /** Whether a Markdown table is a `DataTable` here: it is. */
  def rich(): Boolean = true

  /** `table` drawn as a `DataTable`. */
  def markdown(table: MdBlock.TableBlock, ctx: MarkdownContext, width: => Double): Component = MarkdownTable(table, ctx, width)

  /** Whether `view`, drawn for a table, took `now` in place: rows come and change as an answer streams in. */
  def update(view: Component, now: MdBlock.TableBlock): Boolean = MarkdownTable.update(view, now)

  /** `a` against `b` in the language's own order. */
  def collate(a: String, b: String): Int = {
    val code = everyui.i18n.Strings.language().code.replace('_', '-')
    if (collator == null || collating != code) {
      collator = js.Dynamic.newInstance(js.Dynamic.global.Intl.Collator)(code, js.Dynamic.literal(numeric = true))
      collating = code
    }
    collator.compare(a, b).asInstanceOf[Double].toInt
  }
}
