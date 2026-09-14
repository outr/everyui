package everyui.layout

import everyui.component.{Component, Container}
import everyui.i18n.Direction

/** Along the reading direction (right to left in a right-to-left language), wrapping to a new row when the next child
  * would pass the container's width. */
case class FlowLayout(horizontalSpacing: Double = 0.0, verticalSpacing: Double = 0.0) extends Layout {
  override def fitsWidth(): Boolean = false

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    val width = container.size.width() - pad
    var x = pad
    var y = pad
    var rowHeight = 0.0
    val rtl = Direction.rtl()
    val full = container.size.width()
    children.foreach { child =>
      val w = child.size.width()
      if (x > pad && width > 0.0 && x + w > width) {
        x = pad
        y = y + rowHeight + verticalSpacing
        rowHeight = 0.0
      }
      child.position.left @= (if (rtl) full - x - w else x)
      child.position.top @= y
      x = x + w + horizontalSpacing
      if (child.size.height() > rowHeight) rowHeight = child.size.height()
    }
  }
}
