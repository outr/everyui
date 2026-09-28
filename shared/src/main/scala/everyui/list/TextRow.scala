package everyui.list

import reactify.*

/** A row of text: the item's label, dressed as `decorate` says (an icon, a colour, a badge). What `VirtualList.text`
  * makes. */
class TextRow[T](labelOf: T => String, decorate: T => Decoration = (_: T) => Decoration.plain) extends ListRow[T] {
  val label: RowLabel = new RowLabel(if (editing()) "" else item().map(labelOf).getOrElse(""),
    item().map(decorate).getOrElse(Decoration.plain), TextRow.this.size.width() - ListRow.inset, TextRow.this.size.height())
  label.position.left @= ListRow.inset
  children += label

  override def editStart: Double = ListRow.inset + label.textStart
}
