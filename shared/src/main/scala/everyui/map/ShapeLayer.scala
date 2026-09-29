package everyui.map

import everyui.Color
import everyui.draw.{Drawing, Figure, Path, PathOp}
import everyui.spatial.Rect

/** A map's polygons and lines, drawn as one drawing over the layers' frame in pixels of the screen, so they are crisp
  * and their lines the same width at every zoom. Each is laid out in the world once (`ShapeGeometry`); a frame draws
  * only those that reach into it, each ring thinned to the points a pixel or more apart and cut to the frame where it
  * runs far past it, so a county's outline at a state's zoom is a handful of points and a parcel's edge seen close is
  * not a line a million pixels long. */
private[map] class ShapeLayer {
  val drawing: Drawing = new Drawing
  private var all: Vector[ShapeGeometry] = Vector.empty

  /** The shapes, laid out in the world. */
  def set(shapes: Vector[MapShape]): Unit = all = shapes.map(ShapeGeometry.of)

  /** How many shapes there are, and how many the last frame drew. */
  def count: Int = all.length
  var drawn: Int = 0

  /** Drawn for `frame` at `zoom`. */
  def render(frame: MapFrame, zoom: Double): Unit = {
    drawing.size.width @= frame.width
    drawing.size.height @= frame.height
    val sight = frame.world
    var figures: List[Figure] = Nil
    var count = 0
    all.foreach { g =>
      val style = g.shape.style
      val spare = (style.width + 2.0) / frame.scale
      if (g.shows(zoom) && g.box.grow(spare).intersects(sight)) {
        // cut only what reaches well past the frame: what is inside it is drawn as it is
        val cut = if (sight.grow(sight.width).contains(g.box.x, g.box.y) &&
          sight.grow(sight.width).contains(g.box.right, g.box.bottom)) None
          else Some(Rect(-style.width - 2.0, -style.width - 2.0, frame.width + style.width * 2.0 + 4.0,
            frame.height + style.width * 2.0 + 4.0))
        var ops: Vector[PathOp] = Vector.empty
        g.rings.foreach { r =>
          ShapeLayer.runs(ShapeLayer.thin(r, frame, g.shape.closed), cut, g.shape.closed).foreach { run =>
            var i = 0
            while (i + 1 < run.length) {
              ops = ops :+ PathOp(if (i == 0) "M" else "L", List(run(i), run(i + 1)))
              i += 2
            }
            if (g.shape.closed) ops = ops :+ PathOp("Z", Nil)
          }
        }
        if (ops.nonEmpty) {
          val path = new Path(ops)
          val fill = style.fill.map(c => ShapeLayer.faded(c, style.opacity))
          val stroke = style.stroke.map(c => ShapeLayer.faded(c, style.opacity))
          style.dash match {
            case Some(d) if stroke.nonEmpty =>
              if (fill.nonEmpty) figures = Figure(path, fill = fill) :: figures
              figures = Figure(path.dashed(d.on, d.off), stroke = stroke, width = style.width) :: figures
            case _ => figures = Figure(path, fill = fill, stroke = stroke, width = style.width) :: figures
          }
          count += 1
        }
      }
    }
    drawn = count
    drawing.figures @= figures.reverse
  }

  /** The topmost shape (the last given) that `hits` at the world's point (`x`, `y`) at `zoom`, where a pixel of the
    * screen is `pixel` of the world: within `slop` pixels of its line. */
  def hit(x: Double, y: Double, pixel: Double, slop: Double, zoom: Double): Option[MapShape] = {
    var found: Option[MapShape] = None
    var i = all.length - 1
    while (found.isEmpty && i >= 0) {
      val g = all(i)
      if (g.shape.hits && g.shows(zoom) && g.hits(x, y, (slop + g.shape.style.width / 2.0) * pixel)) found = Some(g.shape)
      i -= 1
    }
    found
  }
}

private[map] object ShapeLayer {
  /** `c` with its alpha taken down by `opacity`. */
  def faded(c: Color, opacity: Double): Color = if (opacity >= 1.0) c else c.withAlpha(c.alpha * math.max(0.0, opacity))

  /** The ring's points in the frame's pixels, each at least a pixel from the one kept before it (a line keeps its
    * last point, so it ends where it should). */
  def thin(ring: Array[Double], frame: MapFrame, closed: Boolean): Array[Double] = {
    val n = ring.length / 2
    val out = new Array[Double](ring.length)
    var kept = 0
    var i = 0
    while (i < n) {
      val x = frame.left(ring(i * 2))
      val y = frame.top(ring(i * 2 + 1))
      val far = kept == 0 || math.abs(x - out(kept * 2 - 2)) + math.abs(y - out(kept * 2 - 1)) >= 1.0
      if (far || (!closed && i == n - 1)) {
        if (!far) kept -= 1
        out(kept * 2) = x
        out(kept * 2 + 1) = y
        kept += 1
      }
      i += 1
    }
    copied(out, kept * 2)
  }

  /** The ring as it is drawn within `cut` (all of it, where there is none): a polygon clipped to the box, still one
    * ring; a line cut into the runs of it that cross the box. A polygon thinned to less than three points is gone. */
  def runs(points: Array[Double], cut: Option[Rect], closed: Boolean): Vector[Array[Double]] =
    if (points.length < (if (closed) 6 else 4)) Vector.empty
    else cut match {
      case None => Vector(points)
      case Some(box) =>
        if (closed) {
          val clipped = clip(points, box)
          if (clipped.length < 6) Vector.empty else Vector(clipped)
        }
        else cutLine(points, box)
    }

  /** A polygon clipped to `box` (Sutherland and Hodgman's way: against each edge in turn). */
  def clip(points: Array[Double], box: Rect): Array[Double] = {
    var ring = points
    var edge = 0
    while (edge < 4 && ring.length >= 6) {
      val n = ring.length / 2
      val out = new Array[Double](n * 4)
      var count = 0
      var i = 0
      while (i < n) {
        val j = if (i + 1 == n) 0 else i + 1
        val ax = ring(i * 2)
        val ay = ring(i * 2 + 1)
        val bx = ring(j * 2)
        val by = ring(j * 2 + 1)
        val aIn = inside(ax, ay, box, edge)
        val bIn = inside(bx, by, box, edge)
        if (aIn) {
          out(count * 2) = ax
          out(count * 2 + 1) = ay
          count += 1
        }
        if (aIn != bIn) {
          val t = crossing(ax, ay, bx, by, box, edge)
          out(count * 2) = ax + (bx - ax) * t
          out(count * 2 + 1) = ay + (by - ay) * t
          count += 1
        }
        i += 1
      }
      ring = copied(out, count * 2)
      edge += 1
    }
    ring
  }

  // which side of the box's edge (left, top, right, bottom) the point is on
  private def inside(x: Double, y: Double, box: Rect, edge: Int): Boolean =
    if (edge == 0) x >= box.x else if (edge == 1) y >= box.y else if (edge == 2) x <= box.right else y <= box.bottom

  // how far along a to b it crosses the edge
  private def crossing(ax: Double, ay: Double, bx: Double, by: Double, box: Rect, edge: Int): Double =
    if (edge == 0) (box.x - ax) / (bx - ax)
    else if (edge == 1) (box.y - ay) / (by - ay)
    else if (edge == 2) (box.right - ax) / (bx - ax)
    else (box.bottom - ay) / (by - ay)

  /** The runs of a line that lie in `box`, each segment cut to it (Liang and Barsky's way). */
  def cutLine(points: Array[Double], box: Rect): Vector[Array[Double]] = {
    var out: Vector[Array[Double]] = Vector.empty
    // the run being made, and how many of its numbers there are
    val run = new Array[Double](points.length * 2)
    var length = 0
    val flush: () => Unit = () => {
      if (length >= 4) out = out :+ copied(run, length)
      length = 0
    }
    val n = points.length / 2
    var i = 0
    while (i + 1 < n) {
      val ax = points(i * 2)
      val ay = points(i * 2 + 1)
      val dx = points(i * 2 + 2) - ax
      val dy = points(i * 2 + 3) - ay
      var t0 = 0.0
      var t1 = 1.0
      var gone = false
      var k = 0
      while (!gone && k < 4) {
        val p = if (k == 0) -dx else if (k == 1) dx else if (k == 2) -dy else dy
        val q = if (k == 0) ax - box.x else if (k == 1) box.right - ax else if (k == 2) ay - box.y else box.bottom - ay
        if (p == 0.0) {
          if (q < 0.0) gone = true
        }
        else {
          val r = q / p
          if (p < 0.0) {
            if (r > t1) gone = true else if (r > t0) t0 = r
          }
          else if (r < t0) gone = true else if (r < t1) t1 = r
        }
        k += 1
      }
      if (gone) flush()
      else {
        if (length == 0 || t0 > 0.0) {
          flush()
          run(0) = ax + dx * t0
          run(1) = ay + dy * t0
          length = 2
        }
        run(length) = ax + dx * t1
        run(length + 1) = ay + dy * t1
        length += 2
        if (t1 < 1.0) flush()
      }
      i += 1
    }
    flush()
    out
  }

  /** The first `length` of `numbers`. */
  def copied(numbers: Array[Double], length: Int): Array[Double] = {
    val out = new Array[Double](length)
    var i = 0
    while (i < length) {
      out(i) = numbers(i)
      i += 1
    }
    out
  }
}
