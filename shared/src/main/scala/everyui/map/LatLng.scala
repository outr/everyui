package everyui.map

/** A place on the Earth: its latitude (`lat`, degrees north, -90 to 90) and longitude (`lng`, degrees east, -180 to
  * 180), as GPS, GeoJSON (which writes them the other way round: `[lng, lat]`) and every tile server give them.
  *
  * {{{
  *   val office = LatLng(35.0844, -106.6504)
  * }}} */
case class LatLng(lat: Double, lng: Double)
