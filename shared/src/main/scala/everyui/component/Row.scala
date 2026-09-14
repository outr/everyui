package everyui.component

import everyui.layout.HorizontalLayout

/** A row of `children`, left to right, sized to them. */
object Row {
  def apply(spacing: Double = 0.0, middle: Boolean = false, padding: Double = 0.0)(children: Component*): Container = {
    val row = new Container
    row.padding @= padding
    row.layout @= Some(HorizontalLayout(spacing, middle))
    row.children ++= children
    row
  }
}
