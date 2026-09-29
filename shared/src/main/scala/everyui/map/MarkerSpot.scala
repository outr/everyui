package everyui.map

import everyui.component.Component

/** Something a map's marker layer draws at a point of the world (`x`, `y`): a marker, or a cluster of them. `id` keeps
  * its component from one frame to the next. */
private[map] class MarkerSpot(val id: String, val x: Double, val y: Double, val feature: MapFeature,
                              val anchor: MapAnchor, val make: () => Component, val hits: Boolean) {
  /** Where its point is on the frame, in pixels. */
  var left: Double = 0.0
  var top: Double = 0.0
}
