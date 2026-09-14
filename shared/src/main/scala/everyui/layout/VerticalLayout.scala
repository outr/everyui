package everyui.layout

import everyui.component.{Component, Container}

/** Top to bottom. */
case class VerticalLayout(spacing: Double = 0.0) extends Layout {
  override def fitsWidth(): Boolean = true

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    var y = pad
    children.foreach { child =>
      child.position.left @= pad
      child.position.top @= y
      y = y + child.size.height() + spacing
    }
  }
}
