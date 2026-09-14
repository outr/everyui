package everyui.layout

import everyui.component.{Component, Container}
import everyui.event.Key

/** youi's layouts, reduced to what a layout knows: where the children go, and so which child is next to which. A
  * container runs its layout whenever its children, its size or a child's size change (`Container.layout`), and sizes
  * itself to the result: its height always, its width when the layout `fitsWidth`.
  *
  * The focus asks the layout too: moving an arrow's way from inside `from` (one of the children), `neighbour` names the
  * child that way, or none when the arrow leaves the container — then the container's own parent is asked. */
trait Layout {
  def layout(container: Container, children: Vector[Component]): Unit
  /** Whether the container's width follows the children (a row, a column) or the children follow it (a flow). */
  def fitsWidth(): Boolean
  /** The child next to `from` in the arrow `direction`'s way, if the arrow stays inside the container. */
  def neighbour(container: Container, children: Vector[Component], from: Component, direction: Key): Option[Component]
}
