package everyui.map

/** Which point of a marker is at its place, as fractions of its size across (`x`) and down (`y`): the middle of a dot
  * (`Center`), the tip at the bottom of a pin (`Bottom`). */
case class MapAnchor(x: Double, y: Double)

object MapAnchor {
  lazy val Center: MapAnchor = MapAnchor(0.5, 0.5)
  lazy val Bottom: MapAnchor = MapAnchor(0.5, 1.0)
  lazy val Top: MapAnchor = MapAnchor(0.5, 0.0)
  lazy val TopLeft: MapAnchor = MapAnchor(0.0, 0.0)
}
