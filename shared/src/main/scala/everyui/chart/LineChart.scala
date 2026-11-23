package everyui.chart

import everyui.draw.{Figure, Path}
import everyui.spatial.Rect
import everyui.theme.Theme
import reactify.*

/** A line for each series across its categories, 2 pixels wide, with a dot at each value (`points`) and the space
  * under each faintly filled (`area`) as options. The categories run evenly from end to end, or, given the moment of
  * each (`times`, epoch milliseconds), along a time axis whose ticks fall on whole hours, days or months. The pointer
  * finds the nearest category, a hairline marks it, and the tip lists every series' value there.
  *
  * A Roku and an Apple TV draw no slanting lines: there each value is a dot.
  *
  * {{{
  *   val hours = LineChart(weekdays, Vector(Series("Meetings", meetingHours), Series("Focus", focusHours)),
  *     ChartFormat.hours)
  *   val mail = LineChart.time(days(), Vector(Series("Work", work()), Series("Personal", personal())))
  * }}} */
class LineChart extends AxisChart {
  override def kind: String = "LineChart"

  /** A dot at each value. */
  val points: Var[Boolean] = own(Var(false))
  /** The space under each line filled faintly in its colour, down to nothing. */
  val area: Var[Boolean] = own(Var(false))
  /** The value axis from nothing, rather than from just under the lowest value (always, with `area`). */
  val fromZero: Var[Boolean] = own(Var(true))
  /** The moment of each category, for a time axis; empty for categories evenly apart. */
  val times: Var[Vector[Long]] = own(Var(Vector.empty))

  private def timed: Boolean = times().size >= 2 && times().size == categories().size

  /** The time axis, where there are `times`. */
  lazy val timeScale: Val[Option[TimeScale]] = own(Val(if (timed) Some(TimeScale(times().min, times().max)) else None))
  private lazy val timeTicks: Val[Vector[Tick]] = own(Val(timeScale() match {
    case Some(t) => t.ticks(math.max(2, math.min(10, (plotWidth / 90.0).toInt)))
    case None => Vector.empty
  }))

  protected def extent: (Double, Double) = {
    val all = eased.now().flatten
    if (all.isEmpty) (0.0, 1.0)
    else if (fromZero() || area()) (math.min(0.0, all.min), math.max(0.0, all.max))
    else (all.min, all.max)
  }
  protected def alongTexts: Vector[String] = if (timeScale().nonEmpty) timeTicks().map(_.label) else categories()
  protected def alongAt(i: Int, g: Rect): Double = timeScale() match {
    case Some(t) => placedTime(t, g)(timeTicks().lift(i).map(_.value).getOrElse(0.0))
    case None => xOf(i, g)
  }
  protected def alongStep(g: Rect): Double =
    if (timeScale().nonEmpty) g.width / math.max(1, timeTicks().size) else g.width / math.max(1, categories().size - 1)
  protected def legendKey: LegendKey = if (area()) LegendKey.Box else LegendKey.Line
  protected def kindWords: String = if (area()) ChartWords.area else ChartWords.line
  protected def pressWords: String = ChartWords.pressPoint

  private def placedTime(t: TimeScale, g: Rect): TimeScale = if (rtl) t.placed(g.right, g.x) else t.placed(g.x, g.right)

  /** Where category `i` is across the frame `g`. */
  def xOf(i: Int, g: Rect): Double = timeScale() match {
    case Some(t) => placedTime(t, g)(times()(i).toDouble)
    case None =>
      val n = categories().size
      val across = if (n <= 1) g.width / 2.0 else g.width * i / (n - 1)
      if (rtl) g.right - across else g.x + across
  }

  /** Each value's point, by series and category. */
  lazy val dots: Val[Vector[Vector[(Double, Double)]]] = own(Val {
    val g = frame()
    val s = placedScale()
    eased.now().map(values => values.indices.toVector.map(i => (xOf(i, g), s(values(i)))))
  })

  protected def marks: Vector[ChartMark] = {
    val all = dots()
    val values = eased.now()
    all.indices.toVector.flatMap(s => all(s).indices.map { i =>
      val (x, y) = all(s)(i)
      ChartMark.around(i, s, values(s)(i), x, y, 0.0, 0.0)
    })
  }

  /** The category nearest across, within half a step of it, and the series whose line is nearest there. */
  override def markAt(x: Double, y: Double): Option[ChartMark] = {
    val all = dots()
    val g = frame()
    if (all.isEmpty || all.head.isEmpty || x < g.x - 12.0 || x > g.right + 12.0) None
    else {
      val xs = all.head.map(_._1)
      val i = xs.indices.minBy(j => math.abs(xs(j) - x))
      val s = all.indices.minBy(k => math.abs(all(k)(i)._2 - y))
      marks.find(m => m.category == i && m.series == s)
    }
  }

  axesReady()
  plot.figures := {
    val g = frame()
    val s = placedScale()
    val all = series()
    val drawn = ChartPaths.drawn
    val baseline = s(if (s.low <= 0.0 && s.high >= 0.0) 0.0 else s.low)
    val surface = Theme.surface()
    val lines = dots().indices.toList.flatMap { k =>
      val pts = dots()(k)
      val c = Chart.colour(all, k)
      val under = if (area() && drawn && pts.size > 1)
        List(Figure(Path.polyline(pts).lineTo(pts.last._1, baseline).lineTo(pts.head._1, baseline).close,
          fill = Some(c.withAlpha(0.12))))
      else Nil
      val line = if (drawn && pts.size > 1) List(Figure(Path.polyline(pts), stroke = Some(c), width = 2.0)) else Nil
      // a dot ringed in the surface where they are asked for, and everywhere a line is not drawn
      val marks = if (points() || !drawn || pts.size == 1)
        pts.toList.flatMap(p => List(Figure(ChartPaths.dot(p._1, p._2, 6.0), fill = Some(surface)),
          Figure(ChartPaths.dot(p._1, p._2, 4.0), fill = Some(c))))
      else Nil
      under ++ line ++ marks
    }
    // a hairline at the category under the pointer, and each series' dot there
    val lit = hovered().toList.flatMap { h =>
      val x = math.round(xOf(h.categoryIndex, g)).toDouble + 0.5
      Figure(Path.line(x, g.y, x, g.bottom), stroke = Some(Theme.muted().withAlpha(0.6))) ::
        dots().indices.toList.flatMap { k =>
          dots()(k).lift(h.categoryIndex).toList.flatMap(p => List(
            Figure(ChartPaths.dot(p._1, p._2, 7.0), fill = Some(surface)),
            Figure(ChartPaths.dot(p._1, p._2, 5.0), fill = Some(Chart.colour(all, k)))))
        }
    }
    val axes = axisFigures
    axes.init ++ (axes.last :: lines) ++ lit
  }
  ready()
}

object LineChart {
  def apply(categories: => Vector[String], series: => Vector[Series], format: Double => String = ChartFormat.compact,
            points: Boolean = false, area: Boolean = false): LineChart = {
    val chart = new LineChart
    chart.categories := categories
    chart.series := series
    chart.format @= format
    chart.points @= points
    chart.area @= area
    chart
  }

  /** A chart over a time axis: a value of each series at each of `times` (epoch milliseconds), each named in the tips
    * and the table by its day, or its time of day where they are all within one. */
  def time(times: => Vector[Long], series: => Vector[Series], format: Double => String = ChartFormat.compact,
           points: Boolean = false, area: Boolean = false): LineChart = {
    val chart = apply(names(times), series, format, points, area)
    chart.times := times
    chart
  }

  /** The names of `times` in the tips and the table: their days, or their times where they share one. */
  def names(times: Vector[Long], offset: Long => Long = everyui.date.CalendarDate.localOffsetMillis): Vector[String] =
    if (times.isEmpty) Vector.empty
    else {
      val oneDay = times.max - times.min < TimeScale.Day
      times.map(t => TimeScale.label(if (oneDay) "h" else "d", t + offset(t)))
    }
}
