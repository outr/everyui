package everyui.map

import everyui.spatial.Rect

/** What a map's layers are laid out for: the world's point at their top left (`x`, `y`), the scale, and how many
  * pixels they cover (`width` by `height`, the view and some to spare round it). A pan within it only moves the
  * layers; a zoom, or a pan past it, lays them out for a new one. */
private[map] case class MapFrame(x: Double, y: Double, scale: Double, width: Double, height: Double) {
  /** The world's `wx` across the layers, in pixels. */
  def left(wx: Double): Double = (wx - x) * scale
  /** The world's `wy` down the layers, in pixels. */
  def top(wy: Double): Double = (wy - y) * scale
  /** The box of the world it covers. */
  def world: Rect = Rect(x, y, width / scale, height / scale)
}
