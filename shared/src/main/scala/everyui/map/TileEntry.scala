package everyui.map

import everyui.component.ImageView
import everyui.spatial.Rect

/** One tile a map has asked for: where its picture is, whether that has been asked for yet (`started`), and has come
  * (`view`, the picture to draw) or failed; the last of the map's passes that wanted or drew it (`used`), which the
  * cache lets go of the oldest by; and where it was last placed. */
private[map] class TileEntry(val tile: MapTile, val address: String) {
  var started: Boolean = false
  var view: Option[ImageView] = None
  var failed: Boolean = false
  var used: Int = 0
  var placed: Option[Rect] = None

  def loaded: Boolean = view.nonEmpty
  /** Whether it has come, one way or the other. */
  def done: Boolean = view.nonEmpty || failed
}
