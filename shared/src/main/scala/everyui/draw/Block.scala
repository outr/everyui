package everyui.draw

import everyui.Color

/** A plain rectangle of colour: what a platform that draws no paths (a Roku, an Apple TV here) shows of a drawing
  * (`Block.of`). */
case class Block(x: Double, y: Double, width: Double, height: Double, color: Color)

object Block {
  /** What of `figures` can be drawn with rectangles alone: each straight stroke that runs across or down, as a
    * rectangle as thick as the stroke, and each filled rectangle. Diagonals and curves are left out: a commit graph's
    * lanes and a bar chart come through, a sparkline's slopes do not. */
  def of(figures: List[Figure]): List[Block] = figures.flatMap { f =>
    val points = corners(f.path)
    val filled: List[Block] = f.fill.toList.flatMap { c =>
      boxOf(points).map { case (x, y, w, h) => Block(x, y, w, h, c) }.toList
    }
    val stroked: List[Block] = f.stroke.toList.flatMap { c =>
      segments(f.path).flatMap { case (x1, y1, x2, y2) =>
        val half = f.width / 2.0
        if (y1 == y2) List(Block(math.min(x1, x2) - half, y1 - half, math.abs(x2 - x1) + f.width, f.width, c))
        else if (x1 == x2) List(Block(x1 - half, math.min(y1, y2) - half, f.width, math.abs(y2 - y1) + f.width, c))
        else Nil
      }
    }
    filled ++ stroked
  }

  /** The straight lines of `path`, each from where the pen was; None where it has curves. */
  private def segments(path: Path): List[(Double, Double, Double, Double)] = {
    var at = (0.0, 0.0)
    var start = (0.0, 0.0)
    var out: List[(Double, Double, Double, Double)] = Nil
    path.ops.foreach { op =>
      op.kind match {
        case "M" =>
          at = (op.values(0), op.values(1))
          start = at
        case "L" =>
          out = (at._1, at._2, op.values(0), op.values(1)) :: out
          at = (op.values(0), op.values(1))
        case "C" => at = (op.values(4), op.values(5))
        case _ =>
          out = (at._1, at._2, start._1, start._2) :: out
          at = start
      }
    }
    out.reverse.filterNot(s => s._1 == s._3 && s._2 == s._4)
  }

  /** The points of a path made only of lines; Nil where it has a curve. */
  private def corners(path: Path): List[(Double, Double)] =
    if (path.ops.exists(_.kind == "C")) Nil
    else path.ops.toList.filter(op => op.kind == "M" || op.kind == "L").map(op => (op.values(0), op.values(1)))

  /** The box of four points that make a rectangle across and down, if they do. */
  private def boxOf(points: List[(Double, Double)]): Option[(Double, Double, Double, Double)] = {
    val corners = points.distinct
    val xs = corners.map(_._1).distinct
    val ys = corners.map(_._2).distinct
    if (corners.size == 4 && xs.size == 2 && ys.size == 2)
      Some((xs.min, ys.min, xs.max - xs.min, ys.max - ys.min))
    else None
  }
}
