package everyui.table

import everyui.component.types.TextAlign

/** A column of a `VirtualTable`: its key (by which its width and its sorting are kept), its heading, its width to
  * start with and the narrowest it may be dragged, how each of its cells is made (`cell`, once per row, reused), and,
  * where it sorts, how. */
case class GridColumn[T](key: String, title: String, width: Double, cell: () => GridCell[T],
                         sort: Option[Ordering[T]] = None, minWidth: Double = 40.0)

object GridColumn {
  /** A column of text, sorted by that text where `sorts` says (or by `sortBy`). */
  def text[T](key: String, title: String, width: Double, sorts: Boolean = false, align: TextAlign = TextAlign.Start,
              sortBy: Option[Ordering[T]] = None)(text: T => String): GridColumn[T] =
    GridColumn[T](key, title, width, () => new TextCell[T](text, align),
      sortBy.orElse(if (sorts) Some(Ordering.by[T, String](text)) else None))
}
