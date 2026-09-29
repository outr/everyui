package everyui.table

import everyui.component.Container
import reactify.*

/** One cell of a `VirtualTable`'s column, made once per row and handed item after item as the rows are reused: bind
  * what it shows to `item` (None while its row has not come from a paged source: the row draws a placeholder over it).
  * `selected` says whether its row is chosen, for a cell that draws its own colours. The table sizes it and clips it.
  *
  * A cell that `edits` is one the keyboard can reach: F2 on a row calls `edit` on its first such cell, which opens what
  * it edits with (a menu under itself, say: `MenuCell`). A pointer reaches it by the cell's own clicks, consumed so
  * that they do not choose the row. */
class GridCell[T] extends Container {
  val item: Var[Option[T]] = own(Var(None))
  val selected: Var[Boolean] = own(Var(false))

  /** Whether F2 on its row edits it. */
  def edits: Boolean = false

  /** Open what it edits with, for its item. */
  def edit(): Unit = ()

  override def kind: String = "GridCell"
}
