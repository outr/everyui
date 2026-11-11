package everyui.draw

/** SVG path data (the `d` of a `<path>`: "M2 2h20v20H2z") as a [[Path]]: every command, absolute and relative, the
  * lines across and down, the quadratic and smooth curves and the elliptical arcs, all made of the four steps every
  * backend draws (a quadratic raised to a cubic, an arc cut into cubics of a quarter turn or less). Numbers may run
  * together as SVG lets them ("a1.64 1.64 0 003.038.002", "M.5.5-1"); the path ends at anything it cannot read, keeping
  * what came before, as a browser does. */
object PathData {
  def parse(d: String): Path = {
    var path = Path.empty
    var at = 0
    // where the pen is, where the figure began, and the last control point (for S and T), with the command it was of
    var x = 0.0
    var y = 0.0
    var startX = 0.0
    var startY = 0.0
    var controlX = 0.0
    var controlY = 0.0
    var last = ' '
    var command = ' '
    var failed = false

    def skip(): Unit = while (at < d.length && (d.charAt(at) <= ' ' || d.charAt(at) == ',')) at += 1
    def digitAt(i: Int): Boolean = i < d.length && d.charAt(i) >= '0' && d.charAt(i) <= '9'
    def digits(): Unit = while (digitAt(at)) at += 1
    def number(): Double = {
      skip()
      val from = at
      if (at < d.length && (d.charAt(at) == '+' || d.charAt(at) == '-')) at += 1
      val whole = digitAt(at)
      digits()
      var fraction = false
      if (at < d.length && d.charAt(at) == '.') {
        at += 1
        fraction = digitAt(at)
        digits()
      }
      if (!whole && !fraction) {
        failed = true
        0.0
      } else {
        if (at < d.length && (d.charAt(at) == 'e' || d.charAt(at) == 'E')) {
          val mark = at
          at += 1
          if (at < d.length && (d.charAt(at) == '+' || d.charAt(at) == '-')) at += 1
          if (digitAt(at)) digits() else at = mark
        }
        // one past what a Double holds (1e999) is no number a path can be drawn with: the path ends before it
        d.substring(from, at).toDoubleOption.filter(v => v - v == 0.0) match {
          case Some(v) => v
          case None =>
            failed = true
            0.0
        }
      }
    }
    // an arc's flags are one digit each, and may run into what follows ("a1 1 0 011 1")
    def flag(): Boolean = {
      skip()
      if (at < d.length && (d.charAt(at) == '0' || d.charAt(at) == '1')) {
        at += 1
        d.charAt(at - 1) == '1'
      } else {
        failed = true
        false
      }
    }
    // whether another set of numbers follows for the same command
    def more(): Boolean = {
      skip()
      at < d.length && (digitAt(at) || d.charAt(at) == '.' || d.charAt(at) == '-' || d.charAt(at) == '+')
    }
    def line(toX: Double, toY: Double): Unit = {
      path = path.lineTo(toX, toY)
      x = toX
      y = toY
    }
    def cubic(x1: Double, y1: Double, x2: Double, y2: Double, toX: Double, toY: Double): Unit = {
      path = path.cubicTo(x1, y1, x2, y2, toX, toY)
      controlX = x2
      controlY = y2
      x = toX
      y = toY
    }
    def quadratic(qx: Double, qy: Double, toX: Double, toY: Double): Unit = {
      cubic(x + 2.0 / 3.0 * (qx - x), y + 2.0 / 3.0 * (qy - y), toX + 2.0 / 3.0 * (qx - toX), toY + 2.0 / 3.0 * (qy - toY),
        toX, toY)
      controlX = qx
      controlY = qy
    }

    skip()
    while (at < d.length && !failed) {
      val c = d.charAt(at)
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
        command = c
        at += 1
      } else if (command == ' ' || command == 'Z' || command == 'z') failed = true
      if (!failed) {
        val relative = command >= 'a'
        val dx = if (relative) x else 0.0
        val dy = if (relative) y else 0.0
        command.toUpper match {
          case 'M' =>
            val (toX, toY) = (number() + dx, number() + dy)
            if (!failed) {
              path = path.moveTo(toX, toY)
              x = toX
              y = toY
              startX = toX
              startY = toY
              // numbers after a move's first pair are lines
              command = if (relative) 'l' else 'L'
            }
          case 'L' =>
            val (toX, toY) = (number() + dx, number() + dy)
            if (!failed) line(toX, toY)
          case 'H' =>
            val toX = number() + dx
            if (!failed) line(toX, y)
          case 'V' =>
            val toY = number() + dy
            if (!failed) line(x, toY)
          case 'C' =>
            val v = List.fill(6)(number())
            if (!failed) cubic(v(0) + dx, v(1) + dy, v(2) + dx, v(3) + dy, v(4) + dx, v(5) + dy)
          case 'S' =>
            val v = List.fill(4)(number())
            // the first control point mirrors the last curve's second, or is the pen where there was no curve
            val smooth = last.toUpper == 'C' || last.toUpper == 'S'
            if (!failed) cubic(if (smooth) 2.0 * x - controlX else x, if (smooth) 2.0 * y - controlY else y,
              v(0) + dx, v(1) + dy, v(2) + dx, v(3) + dy)
          case 'Q' =>
            val v = List.fill(4)(number())
            if (!failed) quadratic(v(0) + dx, v(1) + dy, v(2) + dx, v(3) + dy)
          case 'T' =>
            val v = List.fill(2)(number())
            val smooth = last.toUpper == 'Q' || last.toUpper == 'T'
            if (!failed) quadratic(if (smooth) 2.0 * x - controlX else x, if (smooth) 2.0 * y - controlY else y,
              v(0) + dx, v(1) + dy)
          case 'A' =>
            val (rx, ry, turn) = (number(), number(), number())
            val (large, sweep) = (flag(), flag())
            val (toX, toY) = (number() + dx, number() + dy)
            // (radii of a size whose square no Double holds make no arc but NaN: the path ends before it)
            val curves = if (failed) Nil else arc(x, y, rx, ry, turn, large, sweep, toX, toY)
            if (!curves.forall(_.forall(v => v - v == 0.0))) failed = true
            if (!failed) {
              curves.foreach(v => path = path.cubicTo(v(0), v(1), v(2), v(3), v(4), v(5)))
              if (rx == 0.0 || ry == 0.0) path = path.lineTo(toX, toY)
              x = toX
              y = toY
            }
          case 'Z' =>
            path = path.close
            x = startX
            y = startY
          case _ => failed = true
        }
        last = command
        if (!failed && !more() && at < d.length) {
          val n = d.charAt(at)
          if (!((n >= 'A' && n <= 'Z') || (n >= 'a' && n <= 'z'))) failed = true
        }
      }
    }
    path
  }

  /** The elliptical arc from (`x1`, `y1`) to (`x2`, `y2`) as cubic curves (each the six numbers of a `C`), by the
    * SVG specification's own conversion (its implementation notes, B.2.4): radii too small to reach are scaled up until
    * they do, and an arc with a radius of 0 is no curve at all (a line is drawn instead). */
  def arc(x1: Double, y1: Double, radiusX: Double, radiusY: Double, degrees: Double, large: Boolean, sweep: Boolean,
          x2: Double, y2: Double): List[List[Double]] =
    if ((x1 == x2 && y1 == y2) || radiusX == 0.0 || radiusY == 0.0) Nil
    else {
      val phi = degrees * math.Pi / 180.0
      val (cos, sin) = (math.cos(phi), math.sin(phi))
      val (hx, hy) = ((x1 - x2) / 2.0, (y1 - y2) / 2.0)
      val px = cos * hx + sin * hy
      val py = -sin * hx + cos * hy
      var rx = math.abs(radiusX)
      var ry = math.abs(radiusY)
      val reach = px * px / (rx * rx) + py * py / (ry * ry)
      if (reach > 1.0) {
        rx *= math.sqrt(reach)
        ry *= math.sqrt(reach)
      }
      val num = rx * rx * ry * ry - rx * rx * py * py - ry * ry * px * px
      val den = rx * rx * py * py + ry * ry * px * px
      val k = (if (large != sweep) 1.0 else -1.0) * math.sqrt(math.max(0.0, num / den))
      val (cpx, cpy) = (k * rx * py / ry, -k * ry * px / rx)
      val cx = cos * cpx - sin * cpy + (x1 + x2) / 2.0
      val cy = sin * cpx + cos * cpy + (y1 + y2) / 2.0
      val from = math.atan2((py - cpy) / ry, (px - cpx) / rx)
      var turn = math.atan2((-py - cpy) / ry, (-px - cpx) / rx) - from
      if (sweep && turn < 0.0) turn += 2.0 * math.Pi
      if (!sweep && turn > 0.0) turn -= 2.0 * math.Pi
      val pieces = math.max(1, math.ceil(math.abs(turn) / (math.Pi / 2.0) - 1e-9).toInt)
      val step = turn / pieces
      val t = 4.0 / 3.0 * math.tan(step / 4.0)
      // a point of the unit circle, stretched to the radii, turned and moved to the centre
      def out(ux: Double, uy: Double): List[Double] = List(cx + rx * ux * cos - ry * uy * sin, cy + rx * ux * sin + ry * uy * cos)
      (0 until pieces).toList.map { i =>
        val a = from + step * i
        val b = a + step
        out(math.cos(a) - t * math.sin(a), math.sin(a) + t * math.cos(a)) ++
          out(math.cos(b) + t * math.sin(b), math.sin(b) - t * math.cos(b)) ++ out(math.cos(b), math.sin(b))
      }
    }
}
