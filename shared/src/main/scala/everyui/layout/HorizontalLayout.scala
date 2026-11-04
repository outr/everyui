package everyui.layout

import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.i18n.Direction

/** Along the reading direction — left to right, or right to left in a right-to-left language; with `middle`, each
  * child's middle on the tallest child's. A child with a `flex` (`Component.flex`) is given the width the others
  * leave, where the row has a width of its own. */
case class HorizontalLayout(spacing: Double = 0.0, middle: Boolean = false) extends Layout {
  override def fitsWidth(): Boolean = true

  /** Left and Right along the row as it reads; Up and Down leave it. */
  override def neighbour(container: Container, children: Vector[Component], from: Component, direction: Key): Option[Component] =
    if (direction == Key.ArrowLeft || direction == Key.ArrowRight) Neighbours.along(children, from, Neighbours.forward(direction)) else None

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    // the room left along a row with a width of its own, to the children with a `flex`
    val shared = Layout.share(container, children, spacing, across = true)
    var tallest = 0.0
    var run = 0.0
    var inRow = 0
    children.foreach { child =>
      if (!child.roomless && child.size.height() > tallest) tallest = child.size.height()
      // a zero-WIDTH child takes no horizontal room and books no gap, whatever its height: an empty leading icon
      // must not push a label off-centre. The run must count the same children the positioning pass below advances for.
      if (!child.roomless && (child.size.width() > 0.0 || (shared && child.flexShare > 0.0))) {
        run = run + child.size.width()
        inRow += 1
      }
    }
    if (inRow > 0) run = run + spacing * (inRow - 1)
    // mirrored within the run the children make when the container follows them, else within its own width (which a
    // row sharing its room has)
    val width =
      if (shared) container.size.width()
      else if (container.widthFollowsContent || !container.size.width.explicit) run + pad * 2.0 else container.size.width()
    val rtl = Direction.rtl()
    var x = pad
    children.foreach { child =>
      child.position.left @= (if (rtl) width - x - child.size.width() else x)
      child.position.top @= (if (middle) pad + (tallest - child.size.height()) / 2.0 else pad)
      // a child with no width takes no room and no gap: what a row has nothing to show is left out of it
      if (!child.roomless && (child.size.width() > 0.0 || (shared && child.flexShare > 0.0)))
        x = x + child.size.width() + spacing
    }
  }
}
