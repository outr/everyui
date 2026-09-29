package everyui.spatial

/** A rectangle: its top left corner (`x`, `y`) and its size, in whatever units it is given in (a canvas's world, a
  * drawing's pixels). */
case class Rect(x: Double, y: Double, width: Double, height: Double) {
  def right: Double = x + width
  def bottom: Double = y + height
  def centerX: Double = x + width / 2.0
  def centerY: Double = y + height / 2.0

  def contains(px: Double, py: Double): Boolean = px >= x && px <= right && py >= y && py <= bottom

  /** Whether the two share any area, or touch. */
  def intersects(other: Rect): Boolean =
    other.x <= right && other.right >= x && other.y <= bottom && other.bottom >= y

  /** The smallest rectangle holding both. */
  def union(other: Rect): Rect = {
    val left = math.min(x, other.x)
    val top = math.min(y, other.y)
    Rect(left, top, math.max(right, other.right) - left, math.max(bottom, other.bottom) - top)
  }

  /** Larger by `by` on every side (smaller, where it is negative). */
  def grow(by: Double): Rect = Rect(x - by, y - by, width + by * 2.0, height + by * 2.0)
}

object Rect {
  /** The smallest rectangle holding every one of `rects`; None for none. */
  def around(rects: Iterable[Rect]): Option[Rect] =
    if (rects.isEmpty) None else Some(rects.reduce((a, b) => a.union(b)))
}
