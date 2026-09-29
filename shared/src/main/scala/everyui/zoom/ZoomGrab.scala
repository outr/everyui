package everyui.zoom

/** What a press on a [[ZoomCanvas]] takes hold of in place of a pan (`ZoomCanvas.grab`): a graph's node, dragged.
  * `move` is told where the pointer is in the world as it moves, and `drop` when it lets go. */
case class ZoomGrab(move: (Double, Double) => Unit, drop: () => Unit)
