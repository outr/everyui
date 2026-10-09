package everyui.table

/** A `DataTable`'s rows sorted by `column` (its index), up or down. */
case class ColumnSort(column: Int, ascending: Boolean = true)
