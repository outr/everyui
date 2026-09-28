package everyui.table

import everyui.component.Container
import reactify.*

/** One cell of a `VirtualTable`'s column, made once per row and handed item after item as the rows are reused: bind
  * what it shows to `item`. `selected` says whether its row is chosen, for a cell that draws its own colours. The
  * table sizes it and clips it. */
class GridCell[T] extends Container {
  val item: Var[Option[T]] = own(Var(None))
  val selected: Var[Boolean] = own(Var(false))

  override def kind: String = "GridCell"
}
