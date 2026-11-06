package everyui.layout

import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.i18n.Direction

/** Cards of different heights in columns, each placed in the column that is shortest when it comes, so the columns
  * end close together (a board of cards, a panel of machines). As many columns as fit at least `minColumnWidth` wide
  * with `gap` between them (one, however narrow), sharing the width equally; each child is made a column wide. The
  * first column is at the start of the reading direction. It is laid out again as the width changes, and as a child
  * grows or shrinks (a card unfolding), as every layout is.
  *
  * The order is the children's: Tab and a screen reader go through them as they were added, whichever column each
  * lands in, and so do Left and Right; Up and Down go to the card above or below in the same column.
  *
  * {{{
  *   clients.layout @= Some(MasonryLayout(minColumnWidth = 320.0, gap = 12.0))
  *   clients.children ++= machines.map(m => new MachineCard(m))
  * }}} */
case class MasonryLayout(minColumnWidth: Double, gap: Double = 0.0) extends Layout {
  override def fitsWidth(): Boolean = false

  /** How many columns `width` holds: as many as fit at least `minColumnWidth` wide, with `gap` between them. */
  def columnsFor(width: Double): Int =
    math.max(1, math.floor((width + gap) / math.max(1.0, minColumnWidth + gap)).toInt)

  override def neighbour(container: Container, children: Vector[Component], from: Component,
                         direction: Key): Option[Component] = {
    val shown = children.filter(c => Layout.shows(c) || (c eq from))
    if (direction == Key.ArrowLeft || direction == Key.ArrowRight)
      Neighbours.along(shown, from, Neighbours.forward(direction))
    else {
      // the cards in `from`'s column, top to bottom
      val column = shown.filter(c => math.abs(c.position.left() - from.position.left()) < 1.0)
        .sortBy(_.position.top())
      Neighbours.along(column, from, if (direction == Key.ArrowDown) 1 else -1)
    }
  }

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    val width = container.size.width() - pad * 2.0
    if (width > 0.0) {
      val columns = columnsFor(width)
      val cell = (width - gap * (columns - 1)) / columns
      val rtl = Direction.rtl()
      // how far down each column is filled
      val heights = Array.fill(columns)(pad)
      children.foreach { child =>
        if (child.roomless) {
          child.position.left @= pad
          child.position.top @= pad
        }
        else {
          var col = 0
          var i = 1
          while (i < columns) {
            if (heights(i) < heights(col) - 0.5) col = i
            i += 1
          }
          val x = pad + col * (cell + gap)
          child.size.width @= cell
          child.position.left @= (if (rtl) container.size.width() - x - cell else x)
          child.position.top @= heights(col)
          if (Layout.shows(child)) heights(col) = heights(col) + child.size.height() + gap
        }
      }
    }
  }
}
