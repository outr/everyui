package everyui.graph

import everyui.spatial.Rect

/** Where a graph's layout put everything ([[GraphLayout]]): each node's box, by its index, each edge's route, by its
  * index, the layer each node is in and the nodes of each layer in order (from the top, or from the left), the size of
  * it all, and how many edges cross. */
case class GraphPlan(nodes: Vector[Rect], routes: Vector[GraphRoute], layerOf: Vector[Int], layers: Vector[Vector[Int]],
                     width: Double, height: Double, crossings: Int, flow: GraphFlow)
