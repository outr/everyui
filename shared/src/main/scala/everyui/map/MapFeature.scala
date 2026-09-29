package everyui.map

/** What the pointer found on a map (`MapView.clicked`, `MapView.hovered`, `MapView.featureAt`): a marker, a shape, or
  * a cluster of markers. `key` is the app's name for it. Its cases' names are their own, since a Roku channel's type
  * names are global.
  *
  * {{{
  *   import everyui.map.MapFeature.*
  *   map.clicked.attach(c => c.feature match {
  *     case Some(ShapeFeature(shape)) => open(shape.key)
  *     case Some(MarkerFeature(marker)) => show(marker.key)
  *     case _ => ()
  *   })
  * }}} */
sealed trait MapFeature {
  def key: String
  /** The words the feature's tip shows; empty for none. */
  def label: String
}

object MapFeature {
  /** A marker. */
  case class MarkerFeature(marker: MapMarker) extends MapFeature {
    def key: String = marker.key
    def label: String = marker.label
  }

  /** A polygon or a line. */
  case class ShapeFeature(shape: MapShape) extends MapFeature {
    def key: String = shape.key
    def label: String = shape.label
  }

  /** Markers drawn as one (`MapCluster`): which they are, and the box they are in. */
  case class ClusterFeature(key: String, markers: Vector[MapMarker], bounds: LatLngBounds) extends MapFeature {
    def label: String = markers.length.toString + " here"
  }
}
