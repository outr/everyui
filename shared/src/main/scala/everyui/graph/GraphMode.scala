package everyui.graph

/** How a [[GraphView]] lays its nodes out: in layers along its flow ([[GraphLayout]]), or where forces settle them
  * ([[ForceLayout]]: linked nodes pulled together, the rest pushed apart), for a graph with no one way through it. */
enum GraphMode {
  case Layered, Force
}
