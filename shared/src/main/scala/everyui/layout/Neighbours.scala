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

  /** Children laid out in rows (a flow, a grid): Left and Right move along `from`'s row, and Up and Down to the nearest
    * row that way, onto the child most nearly above or below. */
  def rows(children: Vector[Component], from: Component, direction: Key): Option[Component] = {
    val top = from.position.top()
    if (direction == Key.ArrowLeft || direction == Key.ArrowRight) {
      // the nearest in the row that way, by where it is: right is right whichever way the row reads
      val right = direction == Key.ArrowRight
      val left = from.position.left()
      var best: Option[Component] = None
      children.foreach { c =>
        val l = c.position.left()
        val sameRow = math.abs(c.position.top() - top) < 1.0
        val ahead = if (right) l > left + 1.0 else l < left - 1.0
        if (sameRow && ahead && best.forall(b => if (right) l < b.position.left() else l > b.position.left())) best = Some(c)
      }
      best
    }
    else {
      val down = direction == Key.ArrowDown
      var rowTop: Option[Double] = None
      children.foreach { c =>
        val t = c.position.top()
        val ahead = if (down) t > top + 1.0 else t < top - 1.0
        if (ahead && rowTop.forall(r => if (down) t < r else t > r)) rowTop = Some(t)
      }
      rowTop.flatMap { r =>
        val centre = from.position.left() + from.size.width() / 2.0
        var best: Option[Component] = None
        var gap = 0.0
        children.foreach { c =>
          if (math.abs(c.position.top() - r) < 1.0) {
            val d = math.abs(c.position.left() + c.size.width() / 2.0 - centre)
            if (best.isEmpty || d < gap) {
              best = Some(c)
              gap = d
            }
          }
        }
        best
      }
    }
  }
}
