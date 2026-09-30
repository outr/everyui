package everyui.list

import everyui.component.Component

/** A `VirtualList`'s row standing in for one whose item has not come from its source: the list's `placeholder` in it,
  * sized to the row, over the row's own background, so the cursor and the selection show on it as on any row. It is
  * never handed an item, and the list swaps it for a row of the app's as the item comes. */
class PlaceholderRow[T](val content: Component) extends ListRow[T] {
  content.size.width := size.width()
  content.size.height := size.height()
  children += content

  override def standsIn: Boolean = true
  override def kind: String = "PlaceholderRow"
}
