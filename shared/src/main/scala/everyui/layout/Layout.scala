package everyui.layout

import everyui.component.{Component, Container}

/** youi's layouts, reduced to one method: place `children` inside `container`. A container runs its layout
  * whenever its children, its size or a child's size change (`Container.layout`). */
trait Layout {
  def layout(container: Container, children: Vector[Component]): Unit
}

/** Left to right, wrapping to a new row when the next child would pass the container's width. */
case class FlowLayout(horizontalSpacing: Double = 0.0, verticalSpacing: Double = 0.0) extends Layout {
  override def layout(container: Container, children: Vector[Component]): Unit = {
    val width = container.size.width()
    var x = 0.0
    var y = 0.0
    var rowHeight = 0.0
    children.foreach { child =>
      val w = child.size.width()
      if (x > 0.0 && width > 0.0 && x + w > width) {
        x = 0.0
        y = y + rowHeight + verticalSpacing
        rowHeight = 0.0
      }
      child.position.left @= x
      child.position.top @= y
      x = x + w + horizontalSpacing
      if (child.size.height() > rowHeight) rowHeight = child.size.height()
    }
  }
}

/** Top to bottom. */
case class VerticalLayout(spacing: Double = 0.0) extends Layout {
  override def layout(container: Container, children: Vector[Component]): Unit = {
    var y = 0.0
    children.foreach { child =>
      child.position.top @= y
      y = y + child.size.height() + spacing
    }
  }
}

/** Left to right; with `middle`, each child's middle on the tallest child's. */
case class HorizontalLayout(spacing: Double = 0.0, middle: Boolean = false) extends Layout {
  override def layout(container: Container, children: Vector[Component]): Unit = {
    var x = 0.0
    var tallest = 0.0
    children.foreach(child => if (child.size.height() > tallest) tallest = child.size.height())
    children.foreach { child =>
      child.position.left @= x
      if (middle) child.position.top @= (tallest - child.size.height()) / 2.0
      x = x + child.size.width() + spacing
    }
  }
}
