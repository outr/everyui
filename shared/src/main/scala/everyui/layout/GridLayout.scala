package everyui.layout

import everyui.component.{Component, Container}

/** `columns` equal columns across the container's width, row after row. */
case class GridLayout(columns: Int, horizontalSpacing: Double = 0.0, verticalSpacing: Double = 0.0) extends Layout {
  override def fitsWidth(): Boolean = false

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    val width = container.size.width() - pad * 2.0
    if (width > 0.0 && columns > 0) {
      val cell = (width - horizontalSpacing * (columns - 1)) / columns
      var y = pad
      children.grouped(columns).foreach { row =>
        var rowHeight = 0.0
        row.zipWithIndex.foreach { case (child, col) =>
          child.position.left @= pad + col * (cell + horizontalSpacing)
          child.position.top @= y
          child.size.width @= cell
          if (child.size.height() > rowHeight) rowHeight = child.size.height()
        }
        y = y + rowHeight + verticalSpacing
      }
    }
  }
}
