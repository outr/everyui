package everyui.table

/** The row a `DataTable`'s row menu was opened on (`DataTable.rowMenu`): `row` (its index in `cells`, of a server's
  * rows its place), the cell's `column` where it was opened on a cell (none from the row's ⋮ button), and the `rows`
  * its entries act on: the rows chosen, in the order they show, where `row` is one of them, else `row` alone. */
case class RowRef(row: Int, rows: Vector[Int], column: Option[Int] = None)
