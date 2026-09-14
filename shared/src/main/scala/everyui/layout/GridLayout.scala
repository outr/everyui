package everyui.layout

import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.i18n.Direction

/** `columns` equal columns across the container's width, row after row, the first column at the start of the reading
  * direction. */
case class GridLayout(columns: Int, horizontalSpacing: Double = 0.0, verticalSpacing: Double = 0.0) extends Layout {
  override def fitsWidth(): Boolean = false

  /** Along a row, and to the nearest item in the row above or below. */
  override def neighbour(container: Container, children: Vector[Component], from: Component, direction: Key): Option[Component] =
    Neighbours.rows(children, from, direction)

  override def layout(container: Container, children: Vector[Component]): Unit = {
    val pad = container.padding()
    val width = container.size.width() - pad * 2.0
    if (width > 0.0 && columns > 0) {
      val cell = (width - horizontalSpacing * (columns - 1)) / columns
      val rtl = Direction.rtl()
      var y = pad
      children.grouped(columns).foreach { row =>
        var rowHeight = 0.0
        row.zipWithIndex.foreach { case (child, col) =>
          val x = pad + col * (cell + horizontalSpacing)
          child.position.left @= (if (rtl) container.size.width() - x - cell else x)
          child.position.top @= y
          child.size.width @= cell
          if (child.size.height() > rowHeight) rowHeight = child.size.height()
        }
        y = y + rowHeight + verticalSpacing
      }
    }
  }
}
