package everyui.table

import everyui.i18n.{Icu, Words}

/** What a `DataTable` says, in the language in use (Words' eleven languages): an app gives its own wording or
  * another language with `Words.add` under these keys ("table.copy", …). */
object TableWords {
  private def word(key: String): String = Words(key)
  /** A message with its counts put in, plural where the language says. */
  private def said(key: String, args: Map[String, String]): String = Icu.format(Words(key), args)

  /** The tools' button, to a screen reader and as its tip. */
  def actions: String = word("table.actions")
  def copy: String = word("table.copy")
  def copyMarkdown: String = word("table.copyMarkdown")
  /** "Copy 3 rows", the rows chosen. */
  def copyRows(n: Int): String =
    if (n == 1) word("table.copyRow") else said("table.copyRows", Map("n" -> n.toString))
  def exportCsv: String = word("table.exportCsv")
  def exportMarkdown: String = word("table.exportMarkdown")
  def filter: String = word("table.filter")
  /** "12 of 60" rows, while a filter hides some. */
  def shownOf(n: Int, total: Int): String = said("table.shownOf", Map("n" -> n.toString, "total" -> total.toString))
  /** "60 rows". */
  def rows(n: Int): String =
    if (n == 1) word("table.row") else said("table.rows", Map("n" -> n.toString))
  /** "Copied 12 rows". */
  def copied(n: Int): String = if (n == 1) word("table.copiedRow") else said("table.copied", Map("n" -> n.toString))
  /** "Copied", a cell. */
  def copiedCell: String = word("table.copiedCell")
}
