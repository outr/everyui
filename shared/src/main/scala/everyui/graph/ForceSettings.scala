package everyui.graph

/** How a [[ForceLayout]] moves its nodes.
  *
  *  - `charge`: how hard each node pushes every other away, for a weight of 1 (a node's `weight` times it); the push
  *    falls off with distance, and past `distanceMax` there is none. Worked out over a quadtree (Barnes and Hut's
  *    method): a far group of nodes pushes as one from its centre, once it is `theta` of its distance across or less.
  *  - `linkLength`: how far apart, edge to edge, an edge pulls its ends where it has no length of its own;
  *    `linkStrength`: how hard, times each edge's own strength (and shared out among a busy node's edges).
  *  - `gravity`: how hard each node is pulled towards the middle, which keeps apart pieces from drifting away;
  *    `centred`: the whole graph's middle moved to (0, 0) each tick while nothing is pinned (off: gravity alone keeps
  *    it near there, without the jump a pin let go would make).
  *  - `collide`: how firmly nodes are kept from overlapping, by their sizes and `padding` between them, looked at
  *    `collideIterations` times a tick (more is firmer, for crowded boxes).
  *  - `flow`: for a directed graph, the way its edges lean (each edge's target pushed past its source that way), with
  *    `flowStrength`; None for an undirected one.
  *  - `alphaDecay`: how fast it cools (the default settles in about 300 ticks), `alphaMin`: how cool it stops at,
  *    `reheat`: how warm an update makes it again (lower, less of the rest moves as a node settles in),
  *    `velocityDecay`: the friction on each node's speed, each tick.
  *  - `seed`: where its numbers start: the same seed, the same layout. */
case class ForceSettings(charge: Double = 30.0, theta: Double = 0.9, distanceMax: Option[Double] = None,
                         linkLength: Double = 30.0, linkStrength: Double = 1.0, gravity: Double = 0.02,
                         centred: Boolean = false, collide: Double = 1.0, collideIterations: Int = 2,
                         padding: Double = 2.0,
                         flow: Option[GraphFlow] = None, flowStrength: Double = 0.5, alphaDecay: Double = 0.0228,
                         alphaMin: Double = 0.001, reheat: Double = 0.1, velocityDecay: Double = 0.4, seed: Int = 1)
