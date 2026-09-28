package everyui.draw

/** A path to draw, in the drawing's own pixels (0, 0 at its top left): made step by step, each step a new path, or by
  * the shapes in the companion.
  *
  * {{{
  *   Path.empty.moveTo(0, 10).lineTo(20, 0).cubicTo(30, 0, 30, 20, 40, 20)
  *   Path.circle(8, 8, 4)
  * }}} */
final class Path(val ops: Vector[PathOp]) {
  def moveTo(x: Double, y: Double): Path = add("M", List(x, y))
  def lineTo(x: Double, y: Double): Path = add("L", List(x, y))
  def cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double): Path =
    add("C", List(x1, y1, x2, y2, x, y))
  def close: Path = add("Z", Nil)

  /** An arc of the circle about (`cx`, `cy`) of radius `r`, from angle `from` to `to` in radians (clockwise from three
    * o'clock, as the screen's y runs down), a line joining it to where the path was, unless it starts the path. */
  def arc(cx: Double, cy: Double, r: Double, from: Double, to: Double): Path = {
    val sweep = to - from
    val pieces = math.max(1, math.ceil(math.abs(sweep) / (math.Pi / 2.0) - 1e-9).toInt)
    val step = sweep / pieces
    // a quarter circle or less is one cubic: its control points this far along the tangents
    val k = 4.0 / 3.0 * math.tan(step / 4.0)
    var path = if (ops.isEmpty) moveTo(cx + r * math.cos(from), cy + r * math.sin(from))
      else lineTo(cx + r * math.cos(from), cy + r * math.sin(from))
    var a = from
    var i = 0
    while (i < pieces) {
      val b = a + step
      val (ca, sa, cb, sb) = (math.cos(a), math.sin(a), math.cos(b), math.sin(b))
      path = path.cubicTo(cx + r * (ca - k * sa), cy + r * (sa + k * ca), cx + r * (cb + k * sb), cy + r * (sb - k * cb),
        cx + r * cb, cy + r * sb)
      a = b
      i += 1
    }
    path
  }

  def isEmpty: Boolean = ops.isEmpty

  /** The path as SVG path data ("M 0 10 L 20 0 Z"), which the web and JavaFX draw as it is. */
  def svg: String = ops.map(op => (op.kind :: op.values.map(Path.number)).mkString(" ")).mkString(" ")

  private def add(kind: String, values: List[Double]): Path = new Path(ops :+ PathOp(kind, values))

  override def equals(other: Any): Boolean = other match {
    case p: Path => p.ops == ops
    case _ => false
  }
  override def hashCode: Int = ops.hashCode
}

object Path {
  val empty: Path = new Path(Vector.empty)

  /** Lines through `points`, in order. */
  def polyline(points: Seq[(Double, Double)]): Path = {
    var path = empty
    points.foreach { case (x, y) => path = if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y) }
    path
  }

  def rect(x: Double, y: Double, width: Double, height: Double): Path =
    empty.moveTo(x, y).lineTo(x + width, y).lineTo(x + width, y + height).lineTo(x, y + height).close

  def circle(cx: Double, cy: Double, r: Double): Path = empty.arc(cx, cy, r, 0.0, 2.0 * math.Pi).close

  /** A line from one point to another. */
  def line(x1: Double, y1: Double, x2: Double, y2: Double): Path = empty.moveTo(x1, y1).lineTo(x2, y2)

  /** A number as SVG takes it: whole where it is, and never in exponent form. */
  def number(d: Double): String = {
    // to a thousandth of a pixel, and to a whole one far out, where a Double would be written with an exponent
    val r = if (math.abs(d) >= 1e6) math.rint(d) else math.rint(d * 1000.0) / 1000.0
    if (r == math.rint(r)) r.toLong.toString else r.toString
  }
}
