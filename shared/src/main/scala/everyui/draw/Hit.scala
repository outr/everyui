package everyui.draw

import everyui.spatial.Rect

/** Which of a drawing's figures is at a point, so its shapes can be clicked: inside a figure's fill, or on its stroke
  * (within half its width, and `slop` more). Curves are followed as short lines, a pixel or two long. The point and the
  * figures are in the drawing's own pixels; over a canvas that zooms, `ZoomHit` gives the point there and how large a
  * pixel of the screen is (`ZoomHit.figure`).
  *
  * {{{
  *   Hit.figureAt(drawing.figures(), x, y, slop = 3.0).foreach(i => chosen @= Some(i))
  * }}} */
object Hit {
  /** The topmost of `figures` at (`x`, `y`) (the last drawn), by its index; None where the point is on none. */
  def figureAt(figures: List[Figure], x: Double, y: Double, slop: Double = 0.0): Option[Int] = {
    var found: Option[Int] = None
    var i = 0
    figures.foreach { f =>
      if (on(f, x, y, slop)) found = Some(i)
      i += 1
    }
    found
  }

  /** Whether (`x`, `y`) is on `figure`: inside what it fills, or on what it strokes. */
  def on(figure: Figure, x: Double, y: Double, slop: Double = 0.0): Boolean = {
    val reach = (if (figure.stroke.nonEmpty) figure.width / 2.0 else 0.0) + slop
    bounds(figure.path).exists(_.grow(reach).contains(x, y)) && (
      (figure.fill.nonEmpty && inside(figure.path, x, y)) ||
        ((figure.stroke.nonEmpty || slop > 0.0) && distance(figure.path, x, y) <= reach))
  }

  /** Whether (`x`, `y`) is inside `path` as it is filled: by the nonzero rule, as SVG, JavaFX and Android fill, each of
    * its figures closed. */
  def inside(path: Path, x: Double, y: Double): Boolean = {
    var winding = 0
    lines(path).foreach { case (points, _) =>
      var i = 0
      while (i < points.length) {
        val (x1, y1) = points(i)
        val (x2, y2) = points((i + 1) % points.length)
        if (y1 <= y) {
          if (y2 > y && cross(x1, y1, x2, y2, x, y) > 0.0) winding += 1
        }
        else if (y2 <= y && cross(x1, y1, x2, y2, x, y) < 0.0) winding -= 1
        i += 1
      }
    }
    winding != 0
  }

  /** How far (`x`, `y`) is from the line `path` draws; further than anything (`1e18`) for an empty path. */
  def distance(path: Path, x: Double, y: Double): Double = {
    var nearest = Hit.far
    lines(path).foreach { case (points, closed) =>
      if (points.length == 1) nearest = math.min(nearest, Hit.length(points(0)._1 - x, points(0)._2 - y))
      var i = 0
      val last = if (closed) points.length else points.length - 1
      while (i < last) {
        val (x1, y1) = points(i)
        val (x2, y2) = points((i + 1) % points.length)
        nearest = math.min(nearest, toSegment(x, y, x1, y1, x2, y2))
        i += 1
      }
    }
    nearest
  }

  /** A box round everything `path` draws (its curves' control points with them, so it may be a little large); None for
    * an empty path. */
  def bounds(path: Path): Option[Rect] =
    if (path.ops.forall(_.values.isEmpty)) None
    else {
      var left = Hit.far
      var top = Hit.far
      var right = -Hit.far
      var bottom = -Hit.far
      path.ops.foreach { op =>
        var i = 0
        while (i + 1 < op.values.length) {
          val px = op.values(i)
          val py = op.values(i + 1)
          if (px < left) left = px
          if (px > right) right = px
          if (py < top) top = py
          if (py > bottom) bottom = py
          i += 2
        }
      }
      Some(Rect(left, top, right - left, bottom - top))
    }

  /** The figures of `path` as lines: each its points in order, curves followed in short steps, and whether it was
    * closed. */
  def lines(path: Path): List[(Vector[(Double, Double)], Boolean)] = {
    var out: List[(Vector[(Double, Double)], Boolean)] = Nil
    var points: Vector[(Double, Double)] = Vector.empty
    var at = (0.0, 0.0)
    path.ops.foreach { op =>
      op.kind match {
        case "M" =>
          if (points.nonEmpty) out = (points, false) :: out
          at = (op.values(0), op.values(1))
          points = Vector(at)
        case "L" =>
          at = (op.values(0), op.values(1))
          points = points :+ at
        case "C" =>
          val v = op.values
          val x0 = at._1
          val y0 = at._2
          // steps about two pixels long along the control polygon, which is at least as long as the curve
          val length = Hit.length(v(0) - x0, v(1) - y0) + Hit.length(v(2) - v(0), v(3) - v(1)) +
            Hit.length(v(4) - v(2), v(5) - v(3))
          val steps = math.max(2, math.min(48, (length / 2.0).toInt))
          var s = 1
          while (s <= steps) {
            val t = s.toDouble / steps
            val u = 1.0 - t
            val px = u * u * u * x0 + 3.0 * u * u * t * v(0) + 3.0 * u * t * t * v(2) + t * t * t * v(4)
            val py = u * u * u * y0 + 3.0 * u * u * t * v(1) + 3.0 * u * t * t * v(3) + t * t * t * v(5)
            points = points :+ ((px, py))
            s += 1
          }
          at = (v(4), v(5))
        case _ =>
          if (points.nonEmpty) {
            out = (points, true) :: out
            at = points.head
            points = Vector(at)
          }
      }
    }
    if (points.length > 1) out = (points, false) :: out
    out.reverse
  }

  /** How long the line (0, 0) to (`dx`, `dy`) is (a Roku has no `hypot`). */
  def length(dx: Double, dy: Double): Double = math.sqrt(dx * dx + dy * dy)

  // further than anything drawn (a Roku has no infinity)
  private[draw] def far: Double = 1.0e18

  // which side of the line from (x1, y1) to (x2, y2) the point is: positive to its left
  private def cross(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double): Double =
    (x2 - x1) * (y - y1) - (x - x1) * (y2 - y1)

  private def toSegment(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double = {
    val dx = x2 - x1
    val dy = y2 - y1
    val length = dx * dx + dy * dy
    val t = if (length == 0.0) 0.0 else math.max(0.0, math.min(1.0, ((x - x1) * dx + (y - y1) * dy) / length))
    Hit.length(x - (x1 + t * dx), y - (y1 + t * dy))
  }
}
