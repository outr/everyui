package everyui.list

import everyui.component.Component

/** Rows on their way somewhere: the list they came from and their keys, in its order. */
case class Dragged(from: Component, keys: List[String])
