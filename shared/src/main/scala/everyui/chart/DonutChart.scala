package everyui.chart

import everyui.Color
import everyui.component.TextView
import everyui.component.types.FontWeight
import everyui.draw.Figure
import everyui.spatial.Rect
import everyui.theme.Theme
import reactify.*

/** A whole split into its parts: a ring of slices, one for each of `names`, each its share of the whole (`values`;
  * none below nothing), a 2 pixel gap between each, the total in the middle (`centre`, `caption`), and a legend of
  * each slice's name, value and share beside the ring where the chart is wide and under it where it is not. Past `most`
  * parts the smallest are folded into one, "Other", in the de-emphasis grey.
  *
  * Read at a glance, it says how one whole splits; to compare the parts with each other, ranked bars say it better
  * (`BarChart(…, horizontal = true)`). A Roku and an Apple TV draw no curves: there it is a segmented bar, each part a
  * block of its share, over the same legend.
  *
  * {{{
  *   val time = DonutChart(Vector("Meetings", "Focus", "Mail"), Vector(14.0, 18.5, 6.0), ChartFormat.hours)
  *   time.caption @= "This week"
  * }}} */
class DonutChart extends Chart {
  override def kind: String = "DonutChart"

  val names: Var[Vector[String]] = own(Var(Vector.empty))
  val values: Var[Vector[Double]] = own(Var(Vector.empty))
  /** How much of the radius the hole is: 0 for a pie. */
  val hole: Var[Double] = own(Var(0.62))
  /** What the middle says in large; empty, the total. */
  val centre: Var[String] = own(Var(""))
  /** What the middle says under it, small; empty, "Total". */
  val caption: Var[String] = own(Var(""))
  /** The most parts drawn, the rest folded into "Other". */
  val most: Var[Int] = own(Var(6))

  /** The parts drawn: names and values, the smallest past `most` folded into "Other". */
  lazy val parts: Val[Vector[(String, Double)]] = own(Val(DonutChart.fold(names(), values(), most(), ChartWords.other)))

  private val eased = new Eased(this)
  own(Val(Vector(parts().map(_._2)))).attachAndFire(eased.to)

  private def shown: Vector[Double] = eased.now().headOption.getOrElse(Vector.empty)
  private def total: Double = parts().map(_._2).sum
  private def folded: Boolean = names().size > parts().size
  private def colourOf(i: Int): Color =
    if (folded && i == parts().size - 1) ChartTone.Muted.colour(i) else Theme.series(i)
  private def share(v: Double): String = ChartFormat.percent(if (total > 0.0) v / total else 0.0)

  /** Whether the legend is beside the ring rather than under it. */
  lazy val beside: Val[Boolean] = own(Val(plotWidth >= plotHeight * 1.5))
  private val legend = Legend(parts().indices.toVector.map { i =>
    LegendEntry(parts()(i)._1, colourOf(i), LegendKey.Box, format()(parts()(i)._2) + " · " + share(parts()(i)._2))
  }, column = true)

  /** The ring's box in the plot (where curves are drawn), or the segmented bar's. */
  lazy val ring: Val[Rect] = own(Val {
    val w = plotWidth
    val h = plotHeight
    if (!ChartPaths.drawn) Rect(0.0, 0.0, w, math.min(28.0, h))
    else if (beside()) {
      val d = math.max(0.0, math.min(h, w - legend.size.width() - 24.0))
      Rect(if (rtl) w - d else 0.0, (h - d) / 2.0, d, d)
    }
    else {
      val d = math.max(0.0, math.min(w, h - legend.size.height() - 16.0))
      Rect((w - d) / 2.0, 0.0, d, d)
    }
  })

  private def rtl: Boolean = everyui.i18n.Direction.rtl()

  legend.position.left := {
    val r = ring()
    if (!ChartPaths.drawn || !beside()) (if (rtl) math.max(0.0, plotWidth - legend.size.width()) else 0.0)
    else if (rtl) r.x - 24.0 - legend.size.width() else r.right + 24.0
  }
  legend.position.top := {
    val r = ring()
    if (beside() && ChartPaths.drawn) r.y + (r.height - legend.size.height()) / 2.0 else r.bottom + 16.0
  }
  legend.size.width := math.max(120.0, if (beside()) plotWidth * 0.45 else plotWidth)
  words.children += legend

  private val middle = new TextView {
    direction @= everyui.component.types.TextDirection.Auto
    font.size := math.max(14.0, math.min(30.0, ring().width * 0.14))
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
    content := (if (centre().nonEmpty) centre() else format()(total))
  }
  private val under = Chart.words(TextView.bound(if (caption().nonEmpty) caption() else ChartWords.total, 11.0, Theme.muted()))
  List(middle, under).foreach { t =>
    t.visible := ChartPaths.drawn && hole() >= 0.4
    t.position.left := ring().centerX - t.size.width() / 2.0
    words.children += t
  }
  middle.position.top := ring().centerY - (middle.size.height() + under.size.height()) / 2.0
  under.position.top := middle.position.top() + middle.size.height()

  /** Each part's angle from twelve o'clock, start and end. */
  private def angles: Vector[(Double, Double)] = {
    val v = shown.map(math.max(0.0, _))
    val sum = v.sum
    var at = 0.0
    v.map { x =>
      val sweep = if (sum > 0.0) x / sum * 2.0 * math.Pi else 0.0
      val out = (at, at + sweep)
      at += sweep
      out
    }
  }

  protected def marks: Vector[ChartMark] = {
    val r = ring()
    if (!ChartPaths.drawn) DonutChart.segments(shown, r).zipWithIndex.map { case (b, i) =>
      ChartMark(i, 0, parts().lift(i).map(_._2).getOrElse(0.0), b.x, b.y, b.width, b.height)
    }
    else {
      val radius = r.width / 2.0
      val mid = radius * (1.0 + hole()) / 2.0
      angles.zipWithIndex.map { case ((a, b), i) =>
        val m = (a + b) / 2.0 - math.Pi / 2.0
        ChartMark.around(i, 0, parts().lift(i).map(_._2).getOrElse(0.0), r.centerX + mid * math.cos(m),
          r.centerY + mid * math.sin(m), 0.0, 0.0)
      }
    }
  }

  /** The slice whose ring holds the point, by its angle. */
  override def markAt(x: Double, y: Double): Option[ChartMark] =
    if (!ChartPaths.drawn) ChartMark.at(marks, x, y)
    else {
      val r = ring()
      DonutChart.sliceAt(angles, r.centerX, r.centerY, r.width / 2.0 * hole(), r.width / 2.0, x, y)
        .flatMap(i => marks.find(_.category == i))
    }

  protected def pointedOf(m: ChartMark, pressed: Boolean): Pointed =
    Pointed(parts().lift(m.category).map(_._1).getOrElse(""), "", m.value, m.category, 0, pressed)
  protected def tipOf(p: Pointed): (String, Vector[(Color, String, String)]) =
    (p.category, Vector((colourOf(p.categoryIndex), format()(p.value), share(p.value))))
  protected def kindWords: String = if (hole() > 0.0) ChartWords.donut else ChartWords.pie
  protected def tableNow: ChartTable = ChartTable(Vector(ChartWords.category, ChartWords.value, ChartWords.share),
    parts().map { case (n, v) => Vector(n, format()(v), share(v)) })
  protected def described: String = {
    val ps = parts()
    if (ps.isEmpty) kindWords + ". " + ChartWords.noData
    else kindWords + ": " + ChartWords.total + " " + format()(total) + ". " +
      ps.sortBy(-_._2).map { case (n, v) => n + " " + share(v) }.mkString(", ") + "."
  }

  plot.figures := {
    val r = ring()
    val lit = hovered().map(_.categoryIndex)
    if (!ChartPaths.drawn) DonutChart.segments(shown, r).zipWithIndex.toList.map { case (b, i) =>
      Figure(ChartPaths.bar(b.x, b.y, b.width, b.height, ""), fill = Some(colourOf(i)))
    }
    else {
      val outer = r.width / 2.0
      angles.zipWithIndex.toList.map { case ((a, b), i) =>
        // the slice under the pointer stands a little proud of the ring
        val o = if (lit.contains(i)) outer else outer - 4.0
        Figure(ChartPaths.slice(r.centerX, r.centerY, outer * hole(), o, a, b, 2.0), fill = Some(colourOf(i)))
      }
    }
  }
  ready()
}

object DonutChart {
  def apply(names: => Vector[String], values: => Vector[Double], format: Double => String = ChartFormat.compact,
            hole: Double = 0.62): DonutChart = {
    val chart = new DonutChart
    chart.names := names
    chart.values := values
    chart.format @= format
    chart.hole @= hole
    chart
  }

  /** `names` and `values` paired, none below nothing, in their order; past `most`, the smallest of them folded into
    * one more named `other`, last. */
  def fold(names: Vector[String], values: Vector[Double], most: Int, other: String): Vector[(String, Double)] = {
    val all = names.indices.toVector.map(i => (names(i), math.max(0.0, if (i < values.size) values(i) else 0.0)))
    if (all.size <= most || most < 2) all
    else {
      val kept = all.indices.sortBy(i => -all(i)._2).take(most - 1).toSet
      all.indices.toVector.filter(kept.contains).map(all) :+
        (other, all.indices.filterNot(kept.contains).map(i => all(i)._2).sum)
    }
  }

  /** The slice of those at `angles` (from twelve o'clock, clockwise) whose ring about (`cx`, `cy`), `inner` to `outer`,
    * holds (`x`, `y`), a few pixels either side of it heard too. */
  def sliceAt(angles: Vector[(Double, Double)], cx: Double, cy: Double, inner: Double, outer: Double, x: Double,
              y: Double): Option[Int] = {
    val dx = x - cx
    val dy = y - cy
    val d = math.sqrt(dx * dx + dy * dy)
    if (d < inner - 4.0 || d > outer + 4.0) None
    else {
      // clockwise from twelve o'clock, 0 to two pi
      var a = math.atan2(dy, dx) + math.Pi / 2.0
      if (a < 0.0) a += 2.0 * math.Pi
      val i = angles.indexWhere { case (from, to) => a >= from && a < to }
      if (i < 0) None else Some(i)
    }
  }

  /** The blocks of a segmented bar across `r`, each its share of `values`, 2 pixels apart. */
  def segments(values: Vector[Double], r: Rect): Vector[Rect] = {
    val v = values.map(math.max(0.0, _))
    val sum = v.sum
    val room = r.width - 2.0 * math.max(0, v.count(_ > 0.0) - 1)
    var at = r.x
    v.map { x =>
      val w = if (sum > 0.0) room * x / sum else 0.0
      val out = Rect(at, r.y, w, r.height)
      if (w > 0.0) at += w + 2.0
      out
    }
  }
}
