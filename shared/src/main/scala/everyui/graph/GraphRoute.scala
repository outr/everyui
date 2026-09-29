package everyui.graph

/** The way an edge is drawn (`GraphPlan.routes`): the points it passes from its source's side to its target's, a point
  * either side of each layer it crosses on the way, in the world's units. `reversed` where the layout turned it round to
  * break a cycle (it is still drawn from its source to its target, against the flow); `loop` for an edge from a node to
  * itself, whose one point is the node's corner the loop is drawn at. */
case class GraphRoute(points: Vector[(Double, Double)], reversed: Boolean = false, loop: Boolean = false)
