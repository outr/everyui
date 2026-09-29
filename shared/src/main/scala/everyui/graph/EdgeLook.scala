package everyui.graph

import everyui.Color

/** How a [[GraphView]] draws an edge: its `color` (the theme's muted one where it has none; a rule broken in red,
  * say), `dashed` for one that is planned, and how `wide` its line is. */
case class EdgeLook(color: Option[Color] = None, dashed: Boolean = false, width: Double = 1.5)
