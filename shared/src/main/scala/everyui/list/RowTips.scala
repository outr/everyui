package everyui.list

import everyui.render.Platform
import everyui.tooltip.Tooltip
import reactify.*

/** A list's rows' tips (`ListRow.tip`), shown by `Tooltip` with its delay, look and place: as the pointer rests on a
  * row, as the cursor rests on one in a list with the focus (where the focus shows tips, `Tooltip.onFocus`), and as a
  * finger is held on one where that opens no menu, for `held` seconds. A row is only watched once it has had a tip,
  * so a list without any is as it was. The words are read as the tip shows, so a row handed another item shows its
  * own; and a tip goes as its row is handed another item, as the rows scroll, and with the list. */
private[list] class RowTips[T](list: VirtualList[T]) {
  private var watched: List[ListRow[T]] = Nil
  // the row the pointer is on, and whether the tip asked for last was the cursor's
  private var pointedAt: Option[ListRow[T]] = None
  private var byCursor = false
  private var holds = 0

  /** Row `row`'s pointer heard and its description kept, once. */
  def watch(row: ListRow[T]): Unit = if (!watched.exists(_ eq row)) {
    watched = row :: watched
    row.accessibleDescription := row.tip().getOrElse("")
    if (Platform.pointerSupport()) {
      row.event.pointer.enter.attach { _ =>
        pointedAt = Some(row)
        byCursor = false
        Tooltip.askFor(row, () => row.tip())
      }
      row.event.pointer.leave.attach { _ =>
        if (pointedAt.exists(_ eq row)) pointedAt = None
        if (!byCursor) Tooltip.hideFor(row)
      }
    }
    row.event.pointer.down.attach(_ => Tooltip.hideFor(row))
    row.item.attach { _ =>
      if (Tooltip.shownFor.exists(_ eq row)) Tooltip.hide()
      if (pointedAt.exists(_ eq row)) Tooltip.askFor(row, () => row.tip())
    }
  }

  /** Whether the tip showing is one of this list's. */
  def showing: Boolean = Tooltip.shownFor.exists(t => watched.exists(_ eq t))

  /** The tip taken away, where it is this list's. */
  def hide(): Unit = if (showing) Tooltip.hide()

  /** The rows scrolled: a tip goes with them, and one still to come for the row under the pointer (which the pointer
    * comes onto afresh), and comes again for the cursor where it was the cursor's. */
  def scrolled(): Unit = {
    if (showing || pointedAt.nonEmpty) Tooltip.hide()
    if (byCursor) cursor(true)
  }

  /** The cursor moved, or the focus came or went: the tip of the row it rests on asked for, where `keys` moved it (or
    * the focus) in a list with the focus; else the cursor's taken away. */
  def cursor(keys: Boolean): Unit = {
    val row = list.current().map(list.indexOf).flatMap(list.rowAt)
    if (keys && list.focused() && Tooltip.onFocus() && row.exists(_.tip().nonEmpty)) {
      if (showing && !row.exists(r => Tooltip.shownFor.exists(_ eq r))) Tooltip.hide()
      byCursor = true
      row.foreach(r => Tooltip.askFor(r, () => r.tip()))
    }
    else if (byCursor) {
      byCursor = false
      hide()
    }
  }

  /** A finger held on row `index`, which opened no menu: its tip at once, for `held` seconds. */
  def heldOn(index: Int): Unit = list.rowAt(index).filter(_.tip().nonEmpty).foreach { row =>
    byCursor = false
    Tooltip.askFor(row, () => row.tip(), now = true)
    holds += 1
    val mine = holds
    Platform.after(RowTips.held, () => if (mine == holds && Tooltip.shownFor.exists(_ eq row)) Tooltip.hide())
  }
}

object RowTips {
  /** How long a tip a finger held up stays, in seconds, as Android's own does. */
  val held: Double = 2.5
}
