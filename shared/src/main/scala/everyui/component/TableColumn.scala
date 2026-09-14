package everyui.component

/** A table's column: its title, its width, and the cell for a row. */
case class TableColumn[T](title: String, width: Double, cell: T => Component)
