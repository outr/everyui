package everyui.app

import everyui.draw.{Drawing, Figure, Path}
import everyui.theme.Theme
import reactify.*

/** The zoom demo's network: 24 routers, each linked to its two nearest, drawn in a box 940 by 574 as lines and circles
  * (the links first, then the routers, so a router is drawn over its links). The picked figure, by its index, is drawn
  * in the accent colour. */
object Topology {
  val width: Double = 940.0
  val height: Double = 574.0

  // the routers, scattered the same way every time
  private val routers: Vector[(Double, Double)] = {
    var seed = 7L
    (0 until 24).toVector.map { _ =>
      seed = (seed * 1103515245L + 12345L) % 2147483648L
      val x = 50.0 + (seed % 10000L) / 10000.0 * (width - 100.0)
      seed = (seed * 1103515245L + 12345L) % 2147483648L
      val y = 50.0 + (seed % 10000L) / 10000.0 * (height - 100.0)
      (x, y)
    }
  }

  private val links: Vector[(Int, Int)] = routers.indices.toVector.flatMap { a =>
    routers.indices.filter(_ != a).sortBy(b => distance(a, b)).take(2).map(b => (math.min(a, b), math.max(a, b)))
  }.distinct

  private def distance(a: Int, b: Int): Double =
    math.hypot(routers(a)._1 - routers(b)._1, routers(a)._2 - routers(b)._2)

  /** The drawing, following what is picked. */
  def apply(picked: Var[Option[Int]]): Drawing = {
    val d = new Drawing
    d.figures := figures(picked())
    d
  }

  def figures(picked: Option[Int]): List[Figure] = {
    val lines = links.zipWithIndex.map { case ((a, b), i) =>
      val on = picked.contains(i)
      Figure(Path.line(routers(a)._1, routers(a)._2, routers(b)._1, routers(b)._2),
        stroke = Some(if (on) Theme.accent() else Theme.muted()), width = if (on) 6.0 else 3.0)
    }
    val circles = routers.zipWithIndex.map { case ((x, y), i) =>
      val on = picked.contains(links.size + i)
      Figure(Path.circle(x, y, 16.0), fill = Some(if (on) Theme.accent() else Theme.raised()),
        stroke = Some(if (on) Theme.text() else Theme.accent()), width = 3.0)
    }
    (lines ++ circles).toList
  }

  /** What figure `i` is. */
  def name(i: Int): String =
    if (i < links.size) "Link from router " + (links(i)._1 + 1).toString + " to router " + (links(i)._2 + 1).toString
    else "Router " + (i - links.size + 1).toString
}
