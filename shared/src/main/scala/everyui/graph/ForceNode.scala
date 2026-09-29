package everyui.graph

/** A node of a [[ForceLayout]]: its `key`, which keeps its place from one update to the next, its size (`width` by
  * `height`, round or a box), which other nodes keep clear of, and its `weight`, which says how hard it pushes the
  * others away (2.0 twice as hard as 1.0: a node that stands for more). */
case class ForceNode(key: String, width: Double = 16.0, height: Double = 16.0, weight: Double = 1.0,
                     round: Boolean = true) {
  /** How far from its centre a link to it starts: its radius, or for a box the mean of its half sides. */
  def reach: Double = if (round) math.max(width, height) / 2.0 else (width + height) / 4.0
}

object ForceNode {
  /** A round node, `radius` across from its centre. */
  def circle(key: String, radius: Double, weight: Double = 1.0): ForceNode =
    ForceNode(key, radius * 2.0, radius * 2.0, weight)

  /** A node that is a box, `width` by `height` (a label), kept clear of as a box. */
  def box(key: String, width: Double, height: Double, weight: Double = 1.0): ForceNode =
    ForceNode(key, width, height, weight, round = false)
}
