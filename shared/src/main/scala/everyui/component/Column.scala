package everyui.component

import everyui.layout.VerticalLayout

/** A column of `children`, top to bottom, sized to them; with `center`, each centred on the widest. */
object Column {
  def apply(spacing: Double = 0.0, padding: Double = 0.0, center: Boolean = false)(children: Component*): Container = {
    val column = new Container
    column.padding @= padding
    column.layout @= Some(VerticalLayout(spacing, center))
    column.children ++= children
    column
  }
}
