package everyui.tree

/** A node as a `Tree` shows it, one row of the list: the node (none for the row a new one is being named in), how deep
  * it is, and whether it can open, is open, and is loading its children. */
case class TreeLine[T](key: String, value: Option[T], depth: Int, expandable: Boolean, expanded: Boolean,
                       loading: Boolean)
