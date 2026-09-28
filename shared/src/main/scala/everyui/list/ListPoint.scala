package everyui.list

/** A row asked about at a place: the item under a context menu's click (or the cursor's, for the menu key or a held
  * OK), with its key and where the menu goes, in the root's coordinates. */
case class ListPoint[T](item: T, key: String, x: Double, y: Double)
