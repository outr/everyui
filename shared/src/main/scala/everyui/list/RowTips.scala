package everyui.list

import everyui.component.Component
import everyui.render.Platform
import everyui.tooltip.Tooltip
import reactify.*

/** A list's rows' tips (`ListRow.tip`), shown by `Tooltip` with its delay, look and place: as the pointer rests on a
  * row, as the cursor rests on one in a list with the focus (where the focus shows tips, `Tooltip.onFocus`), and as a
  * finger is held on one where that opens no menu, for `held` seconds. A row is only watched once it has had a tip,
  * so a list without any is as it was. The words are read as the tip shows, so a row handed another item shows its
  * own; and a tip goes as its row is handed another item, as the rows scroll, and with the list.
  *
  * A row with a view of its own for its tip (`Component.tipView`, where views are drawn) shows that instead, for the
  * pointer (by `Tooltip`'s own watch of the row), the cursor and a finger held alike: the view wins, and the row's
  * words (`ListRow.tip`) are only what a screen reader hears. */
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
      // (a finger coming onto a row, on a touch laptop, is no pointer resting on it: a finger held shows its tip; a
      // view the row's own watch shows)
      row.event.pointer.enter.attach(e => if (!e.finger) {
        pointedAt = Some(row)
        byCursor = false
        if (!RowTips.drawn(row)) Tooltip.askFor(row, () => row.tip())
      })
      row.event.pointer.leave.attach { _ =>
        if (pointedAt.exists(_ eq row)) pointedAt = None
        if (!byCursor && !RowTips.drawn(row)) Tooltip.hideFor(row)
      }
    }
    row.event.pointer.down.attach(_ => Tooltip.hideFor(row))
    row.item.attach { _ =>
      if (Tooltip.shownFor.exists(_ eq row)) Tooltip.hideFor(row)
      if (pointedAt.exists(_ eq row)) ask(row)
    }
  }

  // row `row`'s tip asked for, its view where it has one, else its words; at once for a finger held
  private def ask(row: ListRow[T], finger: Boolean = false): Unit =
    if (RowTips.drawn(row)) Tooltip.askView(row, finger)
    else Tooltip.askFor(row, () => row.tip(), now = finger)

  private def ours(t: Component): Boolean = watched.exists(_ eq t)

  /** Whether the tip showing is one of this list's. */
  def showing: Boolean = Tooltip.shownFor.exists(ours)

  /** The tip taken away, where it is this list's, and one still to come for one of its rows; another's is left. */
  def hide(): Unit = {
    Tooltip.shownFor.filter(ours).foreach(Tooltip.hideFor)
    Tooltip.pendingFor.filter(ours).foreach(Tooltip.hideFor)
  }

  /** The rows scrolled: a tip goes with them, and one still to come for the row under the pointer (which the pointer
    * comes onto afresh), and comes again for the cursor where it was the cursor's. */
  def scrolled(): Unit = {
    hide()
    if (byCursor) cursor(true)
  }

  /** The cursor moved, or the focus came or went: the tip of the row it rests on asked for, where `keys` moved it (or
    * the focus) in a list with the focus; else the cursor's taken away. */
  def cursor(keys: Boolean): Unit = {
    val row = list.current().map(list.indexOf).flatMap(list.rowAt)
    if (keys && list.focused() && Tooltip.onFocus() && row.exists(RowTips.has)) {
      Tooltip.shownFor.filter(t => ours(t) && !row.exists(_ eq t)).foreach(Tooltip.hideFor)
      byCursor = true
      row.foreach(r => ask(r))
    }
    else if (byCursor) {
      byCursor = false
      hide()
    }
  }

  /** A finger held on row `index`, which opened no menu: its tip at once, for `held` seconds (its view's, `Tooltip`'s
    * own while, or until a press outside an interactive one). */
  def heldOn(index: Int): Unit = list.rowAt(index).filter(RowTips.has).foreach { row =>
    byCursor = false
    ask(row, finger = true)
    holds += 1
    val mine = holds
    if (!RowTips.drawn(row))
      Platform.after(RowTips.held, () => if (mine == holds && Tooltip.shownFor.exists(_ eq row)) Tooltip.hideFor(row))
  }
}

object RowTips {
  /** How long a tip a finger held up stays, in seconds, as Android's own does. */
  val held: Double = 2.5

  /** Whether `row` shows a view of its own for its tip (`Component.tipView`), where views are drawn. */
  def drawn(row: Component): Boolean = row.tipDrawn && Tooltip.drawsViews

  /** Whether `row` has a tip: a view of its own, or words. */
  def has(row: ListRow[?]): Boolean = drawn(row) || row.tip().nonEmpty
}
