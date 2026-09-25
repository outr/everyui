package everyui.layout

import everyui.component.{Component, Container}
import everyui.event.Key

/** Leaves the children where they were put, and moves the focus along them in their order: Left and Right (as the
  * row reads) when `horizontal`, else Up and Down; the other arrows leave. For a row or a column whose parts are placed
  * by hand (a field with its button at the far end, a row with its menu beside it): without a layout, an arrow finds
  * nothing between them, and a container stating an exit cannot tell which of its parts the arrow left from. */
case class PlacedLayout(horizontal: Boolean = true) extends Layout {
  override def fitsWidth(): Boolean = false

  override def neighbour(container: Container, children: Vector[Component], from: Component, direction: Key): Option[Component] =
    if (horizontal && (direction == Key.ArrowLeft || direction == Key.ArrowRight))
      Neighbours.along(children, from, Neighbours.forward(direction))
    else if (!horizontal && direction == Key.ArrowDown) Neighbours.along(children, from, 1)
    else if (!horizontal && direction == Key.ArrowUp) Neighbours.along(children, from, -1)
    else None

  override def layout(container: Container, children: Vector[Component]): Unit = ()
}
