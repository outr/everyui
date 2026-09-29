package everyui.map

import everyui.spatial.Rect

/** A shape laid out in the world once, as it is given: each ring's points as x, y, x, y... (`rings`), and the box they
  * are all in (`box`), so drawing it and finding it under the pointer project nothing again. */
private[map] class ShapeGeometry(val shape: MapShape, val rings: Vector[Array[Double]], val box: Rect) {
  /** Whether it is drawn at `zoom`. */
  def shows(zoom: Double): Boolean = zoom >= shape.from && shape.until.forall(zoom < _)

  /** Whether the world's point (`x`, `y`) is on it: inside a polygon (filled or not), or within `reach` (in the world's
    * units) of its line. */
  def hits(x: Double, y: Double, reach: Double): Boolean =
    box.grow(reach).contains(x, y) && ((shape.closed && inside(x, y)) || near(x, y, reach))

  // the nonzero rule over every ring, as the shape is filled
  private def inside(x: Double, y: Double): Boolean = {
    var winding = 0
    rings.foreach { r =>
      val n = r.length / 2
      var i = 0
      while (i < n) {
        val x1 = r(i * 2)
        val y1 = r(i * 2 + 1)
        val j = if (i + 1 == n) 0 else i + 1
        val x2 = r(j * 2)
        val y2 = r(j * 2 + 1)
        val cross = (x2 - x1) * (y - y1) - (x - x1) * (y2 - y1)
        if (y1 <= y) {
          if (y2 > y && cross > 0.0) winding += 1
        }
        else if (y2 <= y && cross < 0.0) winding -= 1
        i += 1
      }
    }
    winding != 0
  }

  private def near(x: Double, y: Double, reach: Double): Boolean = {
    var found = false
    val limit = reach * reach
    rings.foreach { r =>
      val n = r.length / 2
      val last = if (shape.closed) n else n - 1
      if (n == 1) found = found || ShapeGeometry.distance2(x, y, r(0), r(1), r(0), r(1)) <= limit
      var i = 0
      while (!found && i < last) {
        val j = if (i + 1 == n) 0 else i + 1
        if (ShapeGeometry.distance2(x, y, r(i * 2), r(i * 2 + 1), r(j * 2), r(j * 2 + 1)) <= limit) found = true
        i += 1
      }
    }
    found
  }
}

private[map] object ShapeGeometry {
  def of(shape: MapShape): ShapeGeometry = {
    var left = 0.0
    var top = 0.0
    var right = 0.0
    var bottom = 0.0
    var any = false
    val rings = shape.rings.filter(_.nonEmpty).map { ring =>
      val out = new Array[Double](ring.length * 2)
      var i = 0
      ring.foreach { p =>
        val x = WebMercator.x(p.lng)
        val y = WebMercator.y(p.lat)
        out(i) = x
        out(i + 1) = y
        if (!any || x < left) left = x
        if (!any || x > right) right = x
        if (!any || y < top) top = y
        if (!any || y > bottom) bottom = y
        any = true
        i += 2
      }
      out
    }
    new ShapeGeometry(shape, rings, Rect(left, top, right - left, bottom - top))
  }

  /** The square of the distance from (`x`, `y`) to the segment from (`x1`, `y1`) to (`x2`, `y2`). */
  def distance2(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double = {
    val dx = x2 - x1
    val dy = y2 - y1
    val length2 = dx * dx + dy * dy
    val t = if (length2 == 0.0) 0.0 else math.max(0.0, math.min(1.0, ((x - x1) * dx + (y - y1) * dy) / length2))
    val ex = x1 + t * dx - x
    val ey = y1 + t * dy - y
    ex * ex + ey * ey
  }
}
