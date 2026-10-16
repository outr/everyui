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

object Layout {
  /** Whether a child takes any room: one with neither width nor height is left out of the spacing, so something a
    * screen has nothing to say about leaves no gap where it would have been. */
  def shows(child: Component): Boolean = child.size.width() > 0.0 || child.size.height() > 0.0

  /** The room left along a row (`across`) or down a column, given to the children with a `flex` (`Component.flex`):
    * what the container's own width or height leaves once the padding, the other children at their sizes and the gaps
    * between all of them are taken out, shared in proportion to their `flex`, none below nothing. Nothing is shared
    * where no child has a `flex`, or where the container is sized to its children (it has no room of its own to share,
    * and its size would follow the very child it sized); false then, and the layout is as it always was. */
  def share(container: Container, children: Vector[Component], spacing: Double, across: Boolean): Boolean = {
    var flexes = 0.0
    children.foreach { c =>
      val f = c.flexShare
      if (f > 0.0) flexes = flexes + f
    }
    if (flexes <= 0.0 || !owns(container, across)) false
    else {
      var used = 0.0
      var count = 0
      children.foreach { c =>
        if (c.flexShare > 0.0) count += 1
        else if (takesRoom(c, across)) {
          used = used + lengthOf(c, across)
          count += 1
        }
      }
      val whole = if (across) container.size.width() else container.size.height()
      val left = whole - container.padding() * 2.0 - used - spacing * (count - 1)
      children.foreach { c =>
        val f = c.flexShare
        if (f > 0.0) {
          val room = math.max(0.0, left * f / flexes)
          if (across) c.size.width @= room else c.size.height @= room
        }
      }
      true
    }
  }

  private def owns(container: Container, across: Boolean): Boolean =
    if (across) container.ownsWidth else container.ownsHeight

  // a row leaves out a child with no width; a column one with no size at all
  private def takesRoom(c: Component, across: Boolean): Boolean = if (across) c.size.width() > 0.0 else shows(c)

  private def lengthOf(c: Component, across: Boolean): Double = if (across) c.size.width() else c.size.height()
}
