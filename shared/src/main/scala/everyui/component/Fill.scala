package everyui.component

/** `child`, given the room left in the row or column it is put in (`Component.flex`): in a column with a height of its
  * own, as tall as the others leave it, and so never under them; with others filling too, a share of that room in
  * proportion to `flex`.
  * {{{
  *   val panel = Column(8.0, padding = 12.0)(filter, Fill(tree))
  *   panel.size.height := host.size.height()
  * }}} */
object Fill {
  def apply[C <: Component](child: C, flex: Double = 1.0): C = {
    child.flex @= flex
    child
  }
}
