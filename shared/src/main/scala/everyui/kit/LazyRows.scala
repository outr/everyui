package everyui.kit

import everyui.component.*
import everyui.focus.Focus
import everyui.render.Platform

/** A long list whose rows are made as it is scrolled toward them rather than all at once: a list of hundreds, each
  * fetching a picture, made whole to show the first dozen holds the page up and asks for every picture together.
  *
  * The rows are handed over as the makings of each (`parts`), in order, and go into `column`, which scrolls in
  * `scroll`. The first `first` are made at once, then more a few at a time on turns of their own for as long as what
  * is made ends less than two screens below what can be seen, so the arrows moving down never reach the end of what
  * has been made. Handed a new list, as many are made at once as were made of the last, so a row the focus was on deep
  * in the list is still there to put it back on. */
class LazyRows(scroll: ScrollContainer, column: Container, first: Int = 24) {
  private var rest: List[() => Component] = Nil
  private var made = 0
  private var filling = false

  scroll.offset.attach(_ => fill())
  // until the list is taken apart: the focus lives on, and a reaction left on it would hold the rows
  scroll.follow(Focus.focused, (_: Option[Component]) => fill())

  /** The rows to put in the column now, of all of `rows`; the rest follow as they are neared. */
  def parts(rows: List[() => Component]): List[Component] = {
    val now = math.max(first, made)
    val built = rows.take(now).map(make => make())
    rest = rows.drop(now)
    made = built.size
    fill()
    built
  }

  /** A few more rows, on a turn of their own, while what is made ends within two screens below what can be seen. */
  private def fill(): Unit = if (!filling && rest.nonEmpty) {
    filling = true
    Platform.later { () =>
      filling = false
      val end = column.position.top() + column.size.height()
      if (rest.nonEmpty && end < scroll.offset() + scroll.size.height() * 3.0) {
        val next = rest.take(LazyRows.batch)
        rest = rest.drop(LazyRows.batch)
        made += next.size
        column.children ++= next.map(make => make())
        fill()
      }
    }
  }
}

object LazyRows {
  /** How many rows are made in a turn. */
  val batch: Int = 8
}
