package everyui.layout

import everyui.component.{Component, Container}
import everyui.event.Key

/** Top to bottom; with `center`, each child centred on the widest (or on the container's own width, when it has one). */
case class VerticalLayout(spacing: Double = 0.0, center: Boolean = false) extends Layout {
  override def fitsWidth(): Boolean = true

  /** Up and Down along the column; Left and Right leave it. */
  override def neighbour(container: Container, children: Vector[Component], from: Component, direction: Key): Option[Component] =
    if (direction == Key.ArrowDown) Neighbours.along(children, from, 1)
    else if (direction == Key.ArrowUp) Neighbours.along(children, from, -1)
    else None

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    var widest = 0.0
    children.foreach(child => if (child.size.width() > widest) widest = child.size.width())
    // centred within the widest child when the container follows its children, else within its own width
    val inner = if (container.widthFollowsContent || !container.size.width.explicit) widest else container.size.width() - pad * 2.0
    var y = pad
    children.foreach { child =>
      child.position.left @= (if (center) pad + (inner - child.size.width()) / 2.0 else pad)
      child.position.top @= y
      // a child with no size takes no room and no gap: an optional line is left out of the column entirely
      if (Layout.shows(child)) y = y + child.size.height() + spacing
    }
  }
}
