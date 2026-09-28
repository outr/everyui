package everyui.tree

/** One node as a `TreeModel` holds it: its value, its parent's key, its children's keys in order, and whether its
  * children are known (loaded, or given). */
class TreeEntry[T](var value: T, var parent: Option[String], var children: Vector[String] = Vector.empty,
                   var loaded: Boolean = false)
