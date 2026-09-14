package everyui.layout

import everyui.component.{Component, Container}

/** Top to bottom; with `center`, each child centred on the widest (or on the container's own width, when it has one). */
case class VerticalLayout(spacing: Double = 0.0, center: Boolean = false) extends Layout {
  override def fitsWidth(): Boolean = true

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
      y = y + child.size.height() + spacing
    }
  }
}
