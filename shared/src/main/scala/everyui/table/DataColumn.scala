package everyui.table

import everyui.component.types.TextAlign
import everyui.text.TextRun

/** A column of a `DataTable`: its `title`, what it holds (`kind`: found from its cells where None, numbers when every
  * cell that says anything is one, dates when every one is a date, else words) and how its cells are aligned (None:
  * numbers at the end, anything else at the start). `runs` draws the title in styled runs where given (a Markdown
  * table's header), and the title is then what is copied and sorted by. */
case class DataColumn(title: String, kind: Option[ColumnKind] = None, align: Option[TextAlign] = None,
                      runs: List[TextRun] = Nil)
