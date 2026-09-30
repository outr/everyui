package everyui.list

import everyui.Color
import everyui.component.Container
import everyui.theme.Theme
import reactify.*

/** One row of a `VirtualList`, made once and handed item after item as the list scrolls: bind what it shows to `item`
  * (`content := item().map(_.name).getOrElse("")`) rather than reading it once. The list sets the rest: where it is
  * (`index`), whether it is chosen (`selected`), under the cursor (`current`), in a list that has the focus
  * (`active`), being renamed (`editing`: hide the label, an editor sits over it), and how a drag would drop onto it
  * (`drop`). The row's own background and edge show the selection and the cursor.
  *
  * Over a source (a server's paged rows), a row not come yet is drawn by the list's `placeholder` instead, unless the
  * list has none: then this row shows it with no item (`present` false). */
class ListRow[T] extends Container {
  val item: Var[Option[T]] = own(Var(None))
  val index: Var[Int] = own(Var(-1))
  val selected: Var[Boolean] = own(Var(false))
  val current: Var[Boolean] = own(Var(false))
  val active: Var[Boolean] = own(Var(false))
  val editing: Var[Boolean] = own(Var(false))
  val drop: Var[Option[DropPosition]] = own(Var(None))
  /** Whether the row has an item to show. */
  val present: Val[Boolean] = own(Val(item().nonEmpty))

  /** Whether this row stands in for one not come (a `PlaceholderRow`). */
  def standsIn: Boolean = false

  /** Where an editor over this row starts, from its start edge (the right, right to left): where the label does. */
  def editStart: Double = ListRow.inset

  backgroundColor := ListRow.fill(selected(), current(), active(), drop().contains(DropPosition.Into))
  borderWidth := (if (current() && active()) 1.0 else 0.0)
  borderColor := Theme.accent()

  override def kind: String = "ListRow"
}

object ListRow {
  /** Where a piece `length` long that starts `start` from a row's start goes, from its left, in a row `width` wide:
    * the same in a left-to-right language, from the right in a right-to-left one. */
  def along(start: Double, length: Double, width: Double): Double =
    if (everyui.i18n.Direction.rtl()) width - start - length else start

  /** Space before the label. */
  val inset: Double = 8.0

  /** A row's background: the accent, faintly, where it is chosen in a list with the focus; the lit control colour where
    * it is chosen in one without, or under the cursor, or a drop would go into it. */
  def fill(selected: Boolean, current: Boolean, active: Boolean, into: Boolean): Color =
    if (into) Theme.accent().withAlpha(0.45)
    else if (selected && active) Theme.accent().withAlpha(0.3)
    else if (selected) Theme.controlLit()
    else if (current && active) Theme.control()
    else Color.Clear
}
