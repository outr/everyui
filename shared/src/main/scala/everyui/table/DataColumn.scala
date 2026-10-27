package everyui.table

import everyui.component.types.TextAlign
import everyui.text.TextRun

/** A column of a `DataTable`: its `title`, what it holds (`kind`: found from its cells where None, numbers when every
  * cell that says anything is one, dates when every one is a date, else words) and how its cells are aligned (None:
  * numbers at the end, anything else at the start). `runs` draws the title in styled runs where given (a Markdown
  * table's header), and the title is then what is copied and sorted by.
  *
  * `key` is what a server knows the column by, for a table of its rows (`DataTable.paged`): its sort is asked by it
  * (`PagedSource.sortable`), and empty is the column's index. `exported` off leaves the column out of what is copied and
  * exported, a row's number beside the data, say: it is shown, sorted and filtered by all the same. */
case class DataColumn(title: String, kind: Option[ColumnKind] = None, align: Option[TextAlign] = None,
                      runs: List[TextRun] = Nil, key: String = "", exported: Boolean = true)
