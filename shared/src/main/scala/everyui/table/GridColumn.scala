package everyui.table

import everyui.component.types.TextAlign

/** A column of a `VirtualTable`: its key (by which its width and its sorting are kept), its heading, its width to
  * start with and the narrowest it may be dragged, how each of its cells is made (`cell`, once per row, reused), and,
  * where it sorts, how. A column that is `sortable` without an ordering is sorted elsewhere: a click on its heading sets
  * the table's `sort`, which a paged table's server hears. Where the table fills its width (`VirtualTable.fills`), a
  * column that `stretches` shares the room in proportion to its width, and one that does not keeps it. Its heading
  * stands where `align` says (at the end over numbers). */
case class GridColumn[T](key: String, title: String, width: Double, cell: () => GridCell[T],
                         sort: Option[Ordering[T]] = None, minWidth: Double = 40.0, sortable: Boolean = false,
                         stretches: Boolean = true, align: TextAlign = TextAlign.Start) {
  /** Whether a click on its heading sorts by it. */
  def sorts: Boolean = sortable || sort.nonEmpty
}

object GridColumn {
  /** A column of text, sorted by that text where `sorts` says (or by `sortBy`). */
  def text[T](key: String, title: String, width: Double, sorts: Boolean = false, align: TextAlign = TextAlign.Start,
              sortBy: Option[Ordering[T]] = None)(text: T => String): GridColumn[T] =
    GridColumn[T](key, title, width, () => new TextCell[T](text, align),
      sortBy.orElse(if (sorts) Some(Ordering.by[T, String](text)) else None), align = align)

  /** A column of text a server sorts (`sortable`), for a paged table. */
  def served[T](key: String, title: String, width: Double, sortable: Boolean = true, align: TextAlign = TextAlign.Start)
               (text: T => String): GridColumn[T] =
    GridColumn[T](key, title, width, () => new TextCell[T](text, align), sortable = sortable, align = align)
}
