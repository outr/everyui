package everyui.table

/** A cell of a `DataTable` typed over in place: its row (its index in the table's `cells`, or of a server's rows, its
  * place), its column, the words it had and those typed. */
case class CellEdit(row: Int, column: Int, before: String, text: String)
