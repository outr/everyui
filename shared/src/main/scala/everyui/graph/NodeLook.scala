package everyui.graph

import everyui.Color

/** How a [[GraphView]] draws a node: in an `accent` colour (its edge and a bar along its leading side), and with a
  * `dashed` outline, for what is planned and not there yet. */
case class NodeLook(accent: Option[Color] = None, dashed: Boolean = false)
