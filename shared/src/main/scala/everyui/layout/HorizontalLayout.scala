package everyui.layout

import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.i18n.Direction

/** Along the reading direction — left to right, or right to left in a right-to-left language; with `middle`, each
  * child's middle on the tallest child's. */
case class HorizontalLayout(spacing: Double = 0.0, middle: Boolean = false) extends Layout {
  override def fitsWidth(): Boolean = true

  /** Left and Right along the row as it reads; Up and Down leave it. */
  override def neighbour(container: Container, children: Vector[Component], from: Component, direction: Key): Option[Component] =
    if (direction == Key.ArrowLeft || direction == Key.ArrowRight) Neighbours.along(children, from, Neighbours.forward(direction)) else None

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    var tallest = 0.0
    var run = 0.0
    children.foreach { child =>
      if (child.size.height() > tallest) tallest = child.size.height()
      run = run + child.size.width()
    }
    if (children.nonEmpty) run = run + spacing * (children.length - 1)
    // mirrored within the run the children make when the container follows them, else within its own width
    val width = if (container.widthFollowsContent || !container.size.width.explicit) run + pad * 2.0 else container.size.width()
    val rtl = Direction.rtl()
    var x = pad
    children.foreach { child =>
      child.position.left @= (if (rtl) width - x - child.size.width() else x)
      child.position.top @= (if (middle) pad + (tallest - child.size.height()) / 2.0 else pad)
      x = x + child.size.width() + spacing
    }
  }
}
