package everyui.list

import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.layout.Layout

/** The layout of what is made lazily (a `LazyColumn`'s rows, a `LazyRail`'s cards): `placeMade` puts what is made where
  * it goes, and `neighbourOf` names the neighbour an arrow goes to, made first where it is not yet, so the focus
  * moves on into what was not made, by the order of the items, never by what sits nearest. */
private[list] class LazyLayout(placeMade: () => Unit, neighbourOf: (Component, Key) => Option[Component],
                               fits: Boolean) extends Layout {
  override def layout(container: Container, children: Vector[Component]): Unit = placeMade()
  override def fitsWidth(): Boolean = fits
  override def neighbour(container: Container, children: Vector[Component], from: Component,
                         direction: Key): Option[Component] = neighbourOf(from, direction)
}
