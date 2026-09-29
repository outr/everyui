package everyui.graph

import everyui.draw.Path
import everyui.spatial.Rect

/** The paths a [[GraphView]] draws its edges with, from their routes (`GraphRoute`): a curve that leaves and reaches
  * each layer square to it, or, where a platform draws no curves, lines across and down; and an arrowhead at the end. */
object GraphShapes {
  /** How long an arrowhead is, along its edge. */
  val arrow: Double = 9.0

  /** The line through `points` along `flow`, stopping an arrowhead short of the last where it has one (`headed`). */
  def line(points: Vector[(Double, Double)], flow: GraphFlow, curved: Boolean, headed: Boolean = true): Path =
    if (points.length < 2) Path.empty
    else {
      val across = flow == GraphFlow.LeftToRight
      val (endX, endY) = if (headed) short(points, across) else points.last
      var path = Path.empty.moveTo(points(0)._1, points(0)._2)
      var i = 1
      while (i < points.length) {
        val (ax, ay) = points(i - 1)
        val (bx0, by0) = points(i)
        val last = i == points.length - 1
        val bx = if (last) endX else bx0
        val by = if (last) endY else by0
        // a step within a layer (a long edge passing a dummy) is straight
        val straight = if (across) math.abs(ay - by) < 0.5 else math.abs(ax - bx) < 0.5
        if (straight) path = path.lineTo(bx, by)
        else if (curved) {
          if (across) {
            val mid = (bx - ax) / 2.0
            path = path.cubicTo(ax + mid, ay, bx - mid, by, bx, by)
          }
          else {
            val mid = (by - ay) / 2.0
            path = path.cubicTo(ax, ay + mid, bx, by - mid, bx, by)
          }
        }
        else if (across) {
          val mid = (ax + bx) / 2.0
          path = path.lineTo(mid, ay).lineTo(mid, by).lineTo(bx, by)
        }
        else {
          val mid = (ay + by) / 2.0
          path = path.lineTo(ax, mid).lineTo(bx, mid).lineTo(bx, by)
        }
        i += 1
      }
      path
    }

  /** The arrowhead at the end of `points`, pointing the way the edge arrives: a triangle, or a square where a platform
    * draws only rectangles. */
  def head(points: Vector[(Double, Double)], flow: GraphFlow, curved: Boolean): Path =
    if (points.length < 2) Path.empty
    else {
      val (tx, ty) = points.last
      val (dx, dy) = direction(points, flow == GraphFlow.LeftToRight)
      if (!curved) Path.rect(tx - dx * arrow - 3.0, ty - dy * arrow - 3.0, 6.0, 6.0)
      else {
        val bx = tx - dx * arrow
        val by = ty - dy * arrow
        val half = arrow * 0.5
        Path.empty.moveTo(tx, ty).lineTo(bx - dy * half, by + dx * half).lineTo(bx + dy * half, by - dx * half).close
      }
    }

  /** The ends of an edge from the box `from` to the box `to`, where nodes lie anywhere (a force layout): where the line
    * between their centres leaves the one and reaches the other; or, where a platform draws only rectangles, from the
    * side of `from` that faces `to` across, and into the side of `to` that faces it up or down. */
  def between(from: Rect, to: Rect, curved: Boolean): Vector[(Double, Double)] =
    if (!curved) {
      val right = to.centerX >= from.centerX
      val down = to.centerY >= from.centerY
      Vector((if (right) from.right else from.x, from.centerY), (to.centerX, if (down) to.y else to.bottom))
    }
    else {
      val dx = to.centerX - from.centerX
      val dy = to.centerY - from.centerY
      Vector(edgeOf(from, dx, dy), edgeOf(to, -dx, -dy))
    }

  /** The line of an edge between two points (`between`), stopping an arrowhead short of the last where it has one
    * (`headed`): straight, or across and then down where a platform draws only rectangles. */
  def straight(points: Vector[(Double, Double)], curved: Boolean, headed: Boolean = true): Path =
    if (points.length < 2) Path.empty
    else {
      val (ax, ay) = points(0)
      val (bx, by) = points(1)
      val (dx, dy) = heading(points, curved)
      val back = if (headed) arrow * 0.8 else 0.0
      val ex = bx - dx * back
      val ey = by - dy * back
      if (curved) Path.empty.moveTo(ax, ay).lineTo(ex, ey)
      else Path.empty.moveTo(ax, ay).lineTo(bx, ay).lineTo(ex, ey)
    }

  /** The arrowhead at the end of a straight edge (`straight`), pointing the way it arrives. */
  def straightHead(points: Vector[(Double, Double)], curved: Boolean): Path =
    if (points.length < 2) Path.empty
    else {
      val (tx, ty) = points(1)
      val (dx, dy) = heading(points, curved)
      if (!curved) Path.rect(tx - dx * arrow - 3.0, ty - dy * arrow - 3.0, 6.0, 6.0)
      else {
        val bx = tx - dx * arrow
        val by = ty - dy * arrow
        val half = arrow * 0.5
        Path.empty.moveTo(tx, ty).lineTo(bx - dy * half, by + dx * half).lineTo(bx + dy * half, by - dx * half).close
      }
    }

  // which way a straight edge arrives, as a unit step: along it, or (drawn across and down) down or up, or across
  // where it has no way down
  private def heading(points: Vector[(Double, Double)], curved: Boolean): (Double, Double) = {
    val (ax, ay) = points(0)
    val (bx, by) = points(1)
    if (!curved) {
      if (math.abs(by - ay) > 0.5) (0.0, if (by > ay) 1.0 else -1.0) else (if (bx >= ax) 1.0 else -1.0, 0.0)
    }
    else {
      val l = math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
      if (l < 1.0e-9) (1.0, 0.0) else ((bx - ax) / l, (by - ay) / l)
    }
  }

  // where a line from the middle of `box`, heading (`dx`, `dy`), leaves it
  private def edgeOf(box: Rect, dx: Double, dy: Double): (Double, Double) = {
    val sx = if (math.abs(dx) < 1.0e-9) 1.0e18 else box.width / 2.0 / math.abs(dx)
    val sy = if (math.abs(dy) < 1.0e-9) 1.0e18 else box.height / 2.0 / math.abs(dy)
    val s = math.min(1.0, math.min(sx, sy))
    (box.centerX + dx * s, box.centerY + dy * s)
  }

  /** A loop from a node's corner (`at`, its top right) back into it. */
  def loop(at: (Double, Double)): Path = {
    val (x, y) = at
    Path.empty.moveTo(x - 18.0, y).cubicTo(x - 18.0, y - 30.0, x + 30.0, y + 12.0, x + arrow, y + 12.0)
  }

  /** The arrowhead of a loop, into the node's side. */
  def loopHead(at: (Double, Double)): Path = {
    val (x, y) = at
    Path.empty.moveTo(x, y + 12.0).lineTo(x + arrow, y + 7.5).lineTo(x + arrow, y + 16.5).close
  }

  // which way the edge arrives: along the flow, forwards or back (an edge turned round to break a cycle)
  private def direction(points: Vector[(Double, Double)], across: Boolean): (Double, Double) = {
    val (tx, ty) = points.last
    val (px, py) = points(points.length - 2)
    if (across) (if (tx >= px) 1.0 else -1.0, 0.0) else (0.0, if (ty >= py) 1.0 else -1.0)
  }

  // where the line stops, an arrowhead short of its end
  private def short(points: Vector[(Double, Double)], across: Boolean): (Double, Double) = {
    val (tx, ty) = points.last
    val (dx, dy) = direction(points, across)
    (tx - dx * arrow * 0.8, ty - dy * arrow * 0.8)
  }
}
