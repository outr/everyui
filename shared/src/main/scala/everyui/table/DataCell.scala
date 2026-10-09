package everyui.table

import everyui.text.TextRun

/** A cell of a `DataTable`: its `text`, which is what is sorted, filtered, copied and exported, and the styled `runs`
  * it is drawn in where given (a Markdown table's code, emphasis and links), else the text as it is. */
case class DataCell(text: String, runs: List[TextRun] = Nil)
