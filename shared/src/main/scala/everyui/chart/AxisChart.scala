package everyui.chart

import everyui.Color
import everyui.draw.{Figure, Path}
import everyui.i18n.Direction
import everyui.spatial.Rect
import everyui.theme.Theme
import reactify.*

/** A chart of series over categories on two axes (`BarChart`, `LineChart`): a value axis with round ticks at the start
  * (the right in a right-to-left language), hairline gridlines at them and a baseline, and the categories along the
  * other axis, their labels thinned or turned where they would run into each other. Its values ease to new ones as the
  * data changes (`Eased`). */
abstract class AxisChart extends Chart {
  val categories: Var[Vector[String]] = own(Var(Vector.empty))
  val series: Var[Vector[Series]] = own(Var(Vector.empty))
  /** The categories down the side and the values along the bottom, rather than across and up. */
  protected val sideways: Var[Boolean] = own(Var(false))

  protected val eased = new Eased(this)
  own(Val(series().map(s => categories().indices.toVector.map(i => Chart.valueAt(s, i))))).attachAndFire(eased.to)

  /** The lowest and highest the value axis must reach for the values as drawn. */
  protected def extent: (Double, Double)
  /** The words along the category axis. */
  protected def alongTexts: Vector[String]
  /** Where word `i` along the category axis is, for the frame `g`. */
  protected def alongAt(i: Int, g: Rect): Double
  /** How far apart the category axis's words are. */
  protected def alongStep(g: Rect): Double
  /** The swatch the legend keys a series with. */
  protected def legendKey: LegendKey

  protected def rtl: Boolean = Direction.rtl()

  /** The value axis: round ticks over what it must reach, about one every 48 pixels up (90 across). */
  lazy val valueScale: Val[LinearScale] = own(Val {
    val (low, high) = extent
    val room = if (sideways()) plotWidth else plotHeight
    LinearScale.nice(low, high, math.max(2, math.min(8, (room / (if (sideways()) 90.0 else 48.0)).toInt)))
  })

  protected val valueWords = new ChartLabels(words, 11.0, (i, v) => {
    v.position.left := {
      val g = frame()
      val s = placedScale()
      if (sideways()) s(s.ticks.lift(i).getOrElse(0.0)) - v.size.width() / 2.0
      else if (rtl) g.right + 8.0 else g.x - 8.0 - v.size.width()
    }
    v.position.top := {
      val g = frame()
      val s = placedScale()
      if (sideways()) g.bottom + 6.0 else s(s.ticks.lift(i).getOrElse(0.0)) - v.size.height() / 2.0
    }
  })
  protected val alongWords = new ChartLabels(words, 11.0, (i, v) => {
    // down the side, a name longer than its share of the width is cut short
    v.maxWidth := (if (sideways()) math.max(0.0, plotWidth * 0.4 - 8.0) else 0.0)
    v.visible := plan().shows(i)
    v.rotation := (if (plan().rotated) (if (rtl) 0.125 else -0.125) else 0.0)
    v.position.left := {
      val g = frame()
      val at = alongAt(i, g)
      if (sideways()) { if (rtl) g.right + 8.0 else g.x - 8.0 - v.size.width() }
      else if (plan().rotated) {
        // turned about its middle, its end (its start, right to left) at the tick
        val half = v.size.width() / 2.0 * math.sqrt(0.5)
        (if (rtl) at + half else at - half) - v.size.width() / 2.0
      }
      else at - v.size.width() / 2.0
    }
    v.position.top := {
      val g = frame()
      if (sideways()) alongAt(i, g) - v.size.height() / 2.0
      else if (plan().rotated) g.bottom + 6.0 + v.size.width() / 2.0 * math.sqrt(0.5) - v.size.height() / 2.0
      else g.bottom + 6.0
    }
  })

  /** How the category axis's words are written so that none runs into the next. */
  lazy val plan: Val[LabelPlan] = own(Val {
    if (sideways()) LabelPlan(1, rotated = false)
    else {
      val room = Rect(0.0, 0.0, math.max(0.0, plotWidth - valueWords.widest - 8.0), plotHeight)
      LabelPlan.of(alongWords.widths, alongWords.lineHeight, alongStep(room), plotHeight * 0.4)
    }
  })

  /** Where the marks are drawn in the plot, inside the axes' words. */
  lazy val frame: Val[Rect] = own(Val {
    val w = plotWidth
    val h = plotHeight
    if (sideways()) {
      val side = math.min(alongWords.widest + 8.0, w * 0.4)
      val bottom = valueWords.lineHeight + 8.0
      val half = valueWords.widest / 2.0
      if (rtl) Rect(half, 4.0, math.max(0.0, w - side - half), math.max(0.0, h - bottom - 4.0))
      else Rect(side, 4.0, math.max(0.0, w - side - half), math.max(0.0, h - bottom - 4.0))
    }
    else {
      val side = valueWords.widest + 8.0
      val p = plan()
      val bottom = (if (p.rotated) LabelPlan.depth(alongWords.widest, alongWords.lineHeight)
        else alongWords.lineHeight) + 8.0
      val top = valueWords.lineHeight / 2.0
      // room at the far end for the last word along, centred on its tick
      val end = if (p.rotated) 0.0 else alongWords.widths.lastOption.getOrElse(0.0) / 2.0
      if (rtl) Rect(end, top, math.max(0.0, w - side - end), math.max(0.0, h - bottom - top))
      else Rect(side, top, math.max(0.0, w - side - end), math.max(0.0, h - bottom - top))
    }
  })

  /** The value axis placed along the frame. */
  lazy val placedScale: Val[LinearScale] = own(Val {
    val g = frame()
    val s = valueScale()
    if (sideways()) { if (rtl) s.placed(g.right, g.x) else s.placed(g.x, g.right) }
    else s.placed(g.bottom, g.y)
  })

  /** The gridlines at the value axis's ticks, and the baseline at nothing (or the axis's low end, where nothing is not
    * on it). */
  protected def axisFigures: List[Figure] = {
    val g = frame()
    val s = placedScale()
    val grid = AxisChart.gridColour()
    val baseAt = if (s.low <= 0.0 && s.high >= 0.0) 0.0 else s.low
    def line(t: Double, colour: Color): Figure = {
      val at = math.round(s(t)).toDouble + 0.5
      if (sideways()) Figure(Path.line(at, g.y, at, g.bottom), stroke = Some(colour))
      else Figure(Path.line(g.x, at, g.right, at), stroke = Some(colour))
    }
    s.ticks.filter(_ != baseAt).toList.map(t => line(t, grid)) :+ line(baseAt, Theme.border())
  }

  protected def pointedOf(m: ChartMark, pressed: Boolean): Pointed = {
    val s = series()
    Pointed(categories().lift(m.category).getOrElse(""), if (s.size > 1) s(m.series).name else "",
      Chart.valueAt(s(m.series), m.category), m.category, m.series, pressed)
  }
  /** Every series' value at the category, the value first. */
  protected def tipOf(p: Pointed): (String, Vector[(Color, String, String)]) = {
    val all = series()
    (p.category, all.indices.toVector.map(i => (Chart.colour(all, i), format()(Chart.valueAt(all(i), p.categoryIndex)),
      if (all.size > 1) all(i).name else "")))
  }
  override protected def legendEntries: Vector[LegendEntry] = {
    val all = series()
    all.indices.toVector.map(i => LegendEntry(all(i).name, Chart.colour(all, i), legendKey))
  }
  protected def tableNow: ChartTable = ChartTable.of(ChartWords.category, categories(), series(), format())
  protected def described: String = Chart.describe(kindWords, categories(), series(), format())

  /** The words along the axes, once all they are placed by is there: called by each kind before `ready`. */
  protected final def axesReady(): Unit = {
    own(Val(valueScale().ticks.map(t => AxisChart.whole(format()(t))))).attachAndFire(valueWords.set)
    own(Val(alongTexts)).attachAndFire(alongWords.set)
  }
}

object AxisChart {
  /** A tick's words without a fraction of nothing: "$100.00" is "$100" and "12.0K" "12K" on an axis, its ticks being
    * round; "1.5 h" is as it was. */
  def whole(words: String): String = {
    val point = words.lastIndexOf('.')
    if (point < 0) words
    else {
      var end = point + 1
      while (end < words.length && words.charAt(end) == '0') end += 1
      val zeros = end > point + 1
      if (zeros && (end == words.length || !words.charAt(end).isDigit)) words.substring(0, point) + words.substring(end)
      else words
    }
  }

  /** A gridline's colour: a step off the surface, a hairline that recedes. */
  def gridColour(): Color = Theme.border().blend(Theme.surface(), 0.5)
}
