package everyui.list

/** A row's new name, typed in place: the item (for a new one, what stood in for it), its key and where it is, the text,
  * and whether it is a new item (`VirtualList.insertEditing`) rather than a rename. */
case class ListEdit[T](item: T, key: String, index: Int, text: String, created: Boolean)
