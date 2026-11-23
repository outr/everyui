package everyui.chart

import everyui.draw.{Figure, Path}
import everyui.spatial.Rect
import everyui.theme.Theme
import reactify.*

/** Bars for each of `categories`, one for each series, side by side (grouped), or one on another (`stacked`), up from
  * a baseline at nothing, or along it (`horizontal`, for many categories or long names). A value axis with round ticks
  * at the start (the right in a right-to-left language), the categories along the other, their labels thinned or
  * turned where they would run into each other, and a legend for two series or more. Each bar is at most 24 pixels
  * thick, its end rounded and its foot square, with a 2 pixel gap between bars that touch.
  *
  * {{{
  *   val spend = BarChart(days(), Vector(Series("Spend", amounts())), ChartFormat.money("$"))
  *   spend.size.width @= 480.0
  *   spend.size.height @= 220.0
  *   spend.stacked @= true
  * }}} */
class BarChart extends AxisChart {
  override def kind: String = "BarChart"

  /** One series on another, rather than side by side. */
  val stacked: Var[Boolean] = own(Var(false))
  /** The categories down the side, their bars along, rather than up from the bottom. */
  val horizontal: Var[Boolean] = sideways

  protected def extent: (Double, Double) = BarChart.extent(eased.now(), stacked())
  protected def alongTexts: Vector[String] = categories()
  protected def alongAt(i: Int, g: Rect): Double = bandIn(g).center(i)
  protected def alongStep(g: Rect): Double = bandIn(g).step
  protected def legendKey: LegendKey = LegendKey.Box
  protected def kindWords: String = ChartWords.bar
  protected def pressWords: String = ChartWords.pressBar

  private def bandIn(g: Rect): BandScale = {
    val n = categories().size
    if (horizontal()) BandScale(n, g.y, g.bottom) else if (rtl) BandScale(n, g.right, g.x) else BandScale(n, g.x, g.right)
  }

  /** Each bar, where it is drawn and the side its rounded end is on. */
  lazy val bars: Val[Vector[(ChartMark, String)]] = own(Val(BarChart.bars(eased.now(), placedScale(), bandIn(frame()),
    stacked(), horizontal())))

  protected def marks: Vector[ChartMark] = bars().map { case (m, _) =>
    ChartMark.around(m.category, m.series, m.value, m.x, m.y, m.width, m.height)
  }

  axesReady()
  plot.figures := {
    val g = frame()
    val all = series()
    // the category under the pointer, lit behind its bars
    val lit = hovered().toList.map { h =>
      val b = bandIn(g)
      val c = b.center(h.categoryIndex)
      val r = if (horizontal()) Rect(g.x, c - b.step / 2.0, g.width, b.step) else Rect(c - b.step / 2.0, g.y, b.step, g.height)
      Figure(Path.rect(r.x, r.y, r.width, r.height), fill = Some(Theme.text().withAlpha(0.06)))
    }
    val drawn = bars().toList.filter(b => b._1.width > 0.0 && b._1.height > 0.0).map { case (m, end) =>
      Figure(ChartPaths.bar(m.x, m.y, m.width, m.height, end), fill = Some(Chart.colour(all, m.series)))
    }
    val axes = axisFigures
    lit ++ axes.init ++ drawn :+ axes.last
  }
  ready()
}
object BarChart {
  /** The gap between bars that touch, and between a stack's segments. */
  val gap: Double = 2.0
  /** The thickest a bar is drawn. */
  val thickest: Double = 24.0

  def apply(categories: => Vector[String], series: => Vector[Series], format: Double => String = ChartFormat.compact,
            stacked: Boolean = false, horizontal: Boolean = false): BarChart = {
    val chart = new BarChart
    chart.categories := categories
    chart.series := series
    chart.format @= format
    chart.stacked @= stacked
    chart.horizontal @= horizontal
    chart
  }

  /** The lowest and highest a value axis must reach for `values` (series by category), nothing among them; stacked,
    * the sums of each category's values above nothing and below it. */
  def extent(values: Vector[Vector[Double]], stacked: Boolean): (Double, Double) = {
    val n = values.foldLeft(0)((m, s) => math.max(m, s.size))
    var low = 0.0
    var high = 0.0
    if (stacked) (0 until n).foreach { i =>
      val at = values.map(s => if (i < s.size) s(i) else 0.0)
      high = math.max(high, at.filter(_ > 0.0).sum)
      low = math.min(low, at.filter(_ < 0.0).sum)
    }
    else values.foreach(_.foreach { v =>
      high = math.max(high, v)
      low = math.min(low, v)
    })
    (low, high)
  }

  /** Each bar of `values` (series by category) as a box on `scale` (placed along the value axis) and `band` (along the
    * categories), with the side its data end is on ("top", "bottom", "left", "right"; "" for a segment inside a
    * stack, which is square). Side by side, a category's bars share its band's middle, each at most `thickest` thick
    * and `gap` apart; stacked, each category's values rise from nothing in order (those below nothing fall from it),
    * a `gap` left between segments. */
  def bars(values: Vector[Vector[Double]], scale: LinearScale, band: BandScale, stacked: Boolean,
           horizontal: Boolean): Vector[(ChartMark, String)] = {
    val k = values.size
    if (k == 0 || band.count == 0) Vector.empty
    else {
      val thick =
        if (stacked) math.min(thickest, band.width)
        else math.max(1.0, math.min(thickest, (band.width - gap * (k - 1)) / k))
      val zero = scale(0.0)
      // a box from `a` to `b` along the value axis, `from` to `from + thick` across it
      def box(c: Int, s: Int, v: Double, a: Double, b: Double, from: Double, end: String): (ChartMark, String) = {
        val lo = math.min(a, b)
        val len = math.abs(b - a)
        if (horizontal) (ChartMark(c, s, v, lo, from, len, thick), end) else (ChartMark(c, s, v, from, lo, thick, len), end)
      }
      // which side a bar's data end is on, for a value above nothing or below it
      def side(v: Double): String = {
        val up = scale(1.0) - scale(0.0)
        if (horizontal) { if ((v >= 0.0) == (up >= 0.0)) "right" else "left" }
        else if ((v >= 0.0) == (up < 0.0)) "top" else "bottom"
      }
      (0 until band.count).toVector.flatMap { c =>
        val middle = band.center(c)
        if (stacked) {
          var above = 0.0
          var below = 0.0
          // the last segment on each side is the one whose end is rounded
          val lastUp = (0 until k).filter(s => value(values, s, c) > 0.0).lastOption
          val lastDown = (0 until k).filter(s => value(values, s, c) < 0.0).lastOption
          (0 until k).toVector.map { s =>
            val v = value(values, s, c)
            val start = if (v >= 0.0) above else below
            val stop = start + v
            if (v >= 0.0) above = stop else below = stop
            val first = start == 0.0
            // the gap at the segment's foot, where another sits under it
            val foot = if (first || v == 0.0) 0.0 else gap * (if (scale(stop) > scale(start)) 1.0 else -1.0)
            val ends = if (lastUp.contains(s) || lastDown.contains(s)) side(v) else ""
            box(c, s, v, scale(start) + foot, scale(stop), middle - thick / 2.0, ends)
          }
        }
        else {
          val group = thick * k + gap * (k - 1)
          (0 until k).toVector.map { s =>
            val v = value(values, s, c)
            box(c, s, v, zero, scale(v), middle - group / 2.0 + s * (thick + gap), side(v))
          }
        }
      }
    }
  }

  private def value(values: Vector[Vector[Double]], s: Int, c: Int): Double =
    if (c < values(s).size) values(s)(c) else 0.0
}
