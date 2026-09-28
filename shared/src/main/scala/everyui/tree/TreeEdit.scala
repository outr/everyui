package everyui.tree

/** A node's name typed in place: the node (none for a new one), the parent it is under and where among its children,
  * and the text. */
case class TreeEdit[T](node: Option[T], parent: Option[String], position: Int, text: String)
