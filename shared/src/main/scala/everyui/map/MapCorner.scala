package everyui.map

/** A corner of a map, where something sits over it (`MapView.legendCorner`): on the `right` or the left, at the
  * `bottom` or the top. */
case class MapCorner(right: Boolean, bottom: Boolean)

object MapCorner {
  lazy val TopLeft: MapCorner = MapCorner(right = false, bottom = false)
  lazy val TopRight: MapCorner = MapCorner(right = true, bottom = false)
  lazy val BottomLeft: MapCorner = MapCorner(right = false, bottom = true)
  lazy val BottomRight: MapCorner = MapCorner(right = true, bottom = true)
}
