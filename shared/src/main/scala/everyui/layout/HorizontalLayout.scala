package everyui.layout

import everyui.component.{Component, Container}

/** Left to right; with `middle`, each child's middle on the tallest child's. */
case class HorizontalLayout(spacing: Double = 0.0, middle: Boolean = false) extends Layout {
  override def fitsWidth(): Boolean = true

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    var x = pad
    var tallest = 0.0
    children.foreach(child => if (child.size.height() > tallest) tallest = child.size.height())
    children.foreach { child =>
      child.position.left @= x
      child.position.top @= (if (middle) pad + (tallest - child.size.height()) / 2.0 else pad)
      x = x + child.size.width() + spacing
    }
  }
}
