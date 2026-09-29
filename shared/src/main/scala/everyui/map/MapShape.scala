package everyui.map

/** A polygon or a line on a map, in latitude and longitude: its `rings`, each a list of places, drawn as `style` says
  * and kept crisp at every zoom. A polygon (`closed`) is filled inside its rings by the nonzero rule, so a ring wound
  * the other way round (as GeoJSON winds a hole) is a hole, and several rings are one shape in several parts; a line
  * (not `closed`) is each ring drawn open.
  *
  * `key` is the app's name for it, which `MapView.clicked` and `MapView.hovered` give back; `label`, where it has one,
  * shows in a tip as the pointer rests on it. It is found by the pointer (inside its fill, or within a few pixels of
  * its line) where it `hits`, and drawn only between the zooms `from` and `until`, so a fine grid can wait until the
  * map is close enough to show it.
  *
  * {{{
  *   MapShape.polygon("parcel-12", ring, MapStyle(fill = Some(teal.withAlpha(0.35)), stroke = Some(teal)),
  *     label = "Parcel 12 (4 records)")
  *   MapShape.line("route", stops, MapStyle(stroke = Some(Color.Blue), width = 3.0, dash = Some(MapDash(6.0, 4.0))))
  * }}} */
case class MapShape(key: String, rings: Vector[Vector[LatLng]], closed: Boolean, style: MapStyle,
                    label: String = "", hits: Boolean = true, from: Double = 0.0, until: Option[Double] = None) {
  /** The box every ring lies in; None for a shape with no places. */
  def bounds: Option[LatLngBounds] = LatLngBounds.around(rings.flatten)
}

object MapShape {
  /** A polygon of one ring. */
  def polygon(key: String, ring: Vector[LatLng], style: MapStyle, label: String = ""): MapShape =
    MapShape(key, Vector(ring), closed = true, style, label)

  /** A line through `points`. */
  def line(key: String, points: Vector[LatLng], style: MapStyle, label: String = ""): MapShape =
    MapShape(key, Vector(points), closed = false, style, label)
}
