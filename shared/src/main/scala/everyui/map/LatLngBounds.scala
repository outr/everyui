package everyui.map

/** A box on the map, between two latitudes (`south`, `north`) and two longitudes (`west`, `east`): what a set of places
  * or shapes covers (`LatLngBounds.around`), what `MapView.fitBounds` shows, and what the view covers
  * (`MapView.bounds`). It does not cross the antimeridian: `west` is no more than `east`.
  *
  * {{{
  *   LatLngBounds.around(parcels.flatMap(_.points)).foreach(b => map.fitBounds(b, 32.0))
  * }}} */
case class LatLngBounds(south: Double, west: Double, north: Double, east: Double) {
  def southWest: LatLng = LatLng(south, west)
  def northEast: LatLng = LatLng(north, east)

  /** The middle of the box as the map draws it: halfway across, and halfway down on the screen (in Web Mercator), which
    * is nearer the pole than the latitude halfway between. */
  def center: LatLng = {
    val (_, top) = WebMercator.toWorld(LatLng(north, west))
    val (_, bottom) = WebMercator.toWorld(LatLng(south, west))
    LatLng(WebMercator.lat((top + bottom) / 2.0), (west + east) / 2.0)
  }

  def contains(p: LatLng): Boolean = p.lat >= south && p.lat <= north && p.lng >= west && p.lng <= east

  /** Whether the two share any area, or touch. */
  def intersects(other: LatLngBounds): Boolean =
    other.west <= east && other.east >= west && other.south <= north && other.north >= south

  /** The smallest box holding both. */
  def union(other: LatLngBounds): LatLngBounds = LatLngBounds(math.min(south, other.south), math.min(west, other.west),
    math.max(north, other.north), math.max(east, other.east))

  /** The smallest box holding this and `p`. */
  def extend(p: LatLng): LatLngBounds = LatLngBounds(math.min(south, p.lat), math.min(west, p.lng),
    math.max(north, p.lat), math.max(east, p.lng))
}

object LatLngBounds {
  /** The smallest box holding every one of `points`; None for none. */
  def around(points: Iterable[LatLng]): Option[LatLngBounds] = {
    var box: Option[LatLngBounds] = None
    points.foreach { p =>
      box = Some(box match {
        case Some(b) => b.extend(p)
        case None => LatLngBounds(p.lat, p.lng, p.lat, p.lng)
      })
    }
    box
  }
}
