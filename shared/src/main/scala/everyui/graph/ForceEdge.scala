package everyui.graph

/** An edge of a [[ForceLayout]], between the nodes keyed `from` and `to`: a spring as strong as `strength` (0.5 half
  * as strong: a weaker tie, a pair seen together less often) that pulls its ends to `length` apart, edge to edge, or
  * the layout's `ForceSettings.linkLength` where it has none. It is directed only where the layout's settings say
  * (`ForceSettings.flow`). */
case class ForceEdge(from: String, to: String, strength: Double = 1.0, length: Option[Double] = None)
