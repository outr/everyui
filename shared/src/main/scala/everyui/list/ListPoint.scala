package everyui.list

/** A row asked about at a place: the item under a context menu's click (or the cursor's, for the menu key or a held
  * OK), with its key and where the menu goes, in the root's coordinates. `pointer` is whether the pointer asked (the
  * secondary button, a finger held), the menu going where it is; else the keys did, and it drops from the row. */
case class ListPoint[T](item: T, key: String, x: Double, y: Double, pointer: Boolean = false)
