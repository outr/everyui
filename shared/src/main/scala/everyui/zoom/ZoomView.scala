package everyui.zoom

/** Where a [[ZoomCanvas]] looks: the world's point at its top left corner (`x`, `y`), and how many of the screen's
  * pixels a unit of the world takes (`scale`: 2.0 is twice as close). */
case class ZoomView(x: Double, y: Double, scale: Double)
