package everyui.table

/** What a `DataTable`'s Revert asks the app to put back (`DataTable.onRevert`): the cell of `column` of row `row`, or,
  * with no column, the whole row (its changed cells, a new row taken out, a deleted one kept). */
case class Revert(row: Int, column: Option[Int] = None)
