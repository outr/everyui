package everyui.layout

import everyui.component.{Component, Container}

/** youi's layouts, reduced to one method: place `children` inside `container`, starting at its padding. A
  * container runs its layout whenever its children, its size or a child's size change (`Container.layout`), and
  * sizes itself to the result: its height always, its width when the layout `fitsWidth`. */
trait Layout {
  def layout(container: Container, children: Vector[Component]): Unit
  /** Whether the container's width follows the children (a row, a column) or the children follow it (a flow). */
  def fitsWidth(): Boolean
}
