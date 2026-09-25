package everyui.layout

import everyui.component.Component
import everyui.event.Key
import everyui.i18n.Direction

/** How layouts find a child's neighbour: along a line of children, and across the rows of a wrapping one. */
object Neighbours {
  /** The child at `from`'s index plus `step`, if there is one. */
  def along(children: Vector[Component], from: Component, step: Int): Option[Component] = {
    var index = -1
    children.zipWithIndex.foreach { case (c, i) => if (c eq from) index = i }
    val next = index + step
    if (index < 0 || next < 0 || next >= children.length) None else Some(children(next))
  }

  /** In the reading direction, Right is the next child and Left the one before; right to left, the other way. */
  def forward(direction: Key): Int = {
    val right = if (direction == Key.ArrowRight) 1 else -1
    if (Direction.rtl()) -right else right
  }

  /** Children laid out in rows (a flow, a grid), as the layout placed them: a row is a run of children on one line.
    * Left and Right move along the row in the reading direction; Up and Down to the row before or after, at the same
    * place in it (the last, where that row is shorter). By the rows' order, never by what sits nearest. */
  def rows(children: Vector[Component], from: Component, direction: Key): Option[Component] = {
    val shown = children.filter(c => Layout.shows(c) || (c eq from))
    var lines: Vector[Vector[Component]] = Vector.empty
    shown.foreach { c =>
      if (lines.nonEmpty && math.abs(lines.last.head.position.top() - c.position.top()) < 1.0) lines = lines.init :+ (lines.last :+ c)
      else lines = lines :+ Vector(c)
    }
    var row = -1
    var at = -1
    lines.zipWithIndex.foreach { case (line, r) => line.zipWithIndex.foreach { case (c, i) => if (c eq from) { row = r; at = i } } }
    if (row < 0) None
    else if (direction == Key.ArrowLeft || direction == Key.ArrowRight) {
      val next = at + forward(direction)
      if (next < 0 || next >= lines(row).length) None else Some(lines(row)(next))
    }
    else {
      val r = if (direction == Key.ArrowDown) row + 1 else row - 1
      if (r < 0 || r >= lines.length) None else Some(lines(r)(math.min(at, lines(r).length - 1)))
    }
  }
}
