package everyui.table

import everyui.text.TextRun

/** A cell of a `DataTable`: its `text`, which is what is sorted, filtered, copied and exported, and the styled `runs`
  * it is drawn in where given (a Markdown table's code, emphasis and links), else the text as it is. A cell of
  * `nothing` holds no value at all (a database's NULL, `DataCell.Null`): it is drawn as NULL in the muted colour, read
  * to a screen reader as "null" (`TableWords.nothing`), and sorted, filtered, copied and exported as a blank cell. */
case class DataCell(text: String, runs: List[TextRun] = Nil, nothing: Boolean = false)

object DataCell {
  /** A cell holding no value: a database's NULL. */
  val Null: DataCell = DataCell("", nothing = true)
  /** A cell of `value`'s words where it has any, else `Null`. */
  def of(value: Option[String]): DataCell = value.map(v => DataCell(v)).getOrElse(Null)
  /** What a cell holding no value shows: the word a database writes for it, the same in every language. */
  val nullText: String = "NULL"
}
