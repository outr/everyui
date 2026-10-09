package everyui.table

import everyui.i18n.Words

/** What a `DataTable` says, in the language in use: an app gives its translations with `Words.add` under these keys
  * ("table.copy", …), and English is said where none is given. */
object TableWords {
  private def word(key: String, english: String): String = {
    val said = Words(key)
    if (said == key) english else said
  }

  /** The tools' button, to a screen reader and as its tip. */
  def actions: String = word("table.actions", "Table actions")
  def copy: String = word("table.copy", "Copy table")
  def copyMarkdown: String = word("table.copyMarkdown", "Copy as Markdown")
  /** "Copy 3 rows", the rows chosen. */
  def copyRows(n: Int): String =
    if (n == 1) word("table.copyRow", "Copy 1 row") else word("table.copyRows", "Copy {n} rows").replace("{n}", n.toString)
  def exportCsv: String = word("table.exportCsv", "Download CSV")
  def exportMarkdown: String = word("table.exportMarkdown", "Download Markdown")
  def filter: String = word("table.filter", "Filter rows")
  /** "12 of 60" rows, while a filter hides some. */
  def shownOf(n: Int, total: Int): String =
    word("table.shownOf", "{n} of {total}").replace("{n}", n.toString).replace("{total}", total.toString)
  /** "60 rows". */
  def rows(n: Int): String =
    if (n == 1) word("table.row", "1 row") else word("table.rows", "{n} rows").replace("{n}", n.toString)
  /** "Copied 12 rows". */
  def copied(n: Int): String =
    if (n == 1) word("table.copiedRow", "Copied 1 row")
    else word("table.copied", "Copied {n} rows").replace("{n}", n.toString)
  /** "Copied", a cell. */
  def copiedCell: String = word("table.copiedCell", "Copied")
}
