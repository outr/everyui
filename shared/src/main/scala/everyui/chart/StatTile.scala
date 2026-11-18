package everyui.chart

import everyui.Color
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.draw.{Drawing, Figure, Path, Sparkline}
import everyui.font.MaterialIcon
import everyui.kit.Mirror
import everyui.layout.VerticalLayout
import everyui.theme.Theme
import reactify.*

/** One number that matters: its `label`, its `value` large (already written as it should read, "$1,284", "12.9K"),
  * how it moved (`delta`, signed, written by `deltaFormat`, against `period`: "vs last week") with an arrow, in the
  * success tone where the move is good and the danger tone where it is not (`upIsGood` says which way is good), and,
  * where there is one, its `trend` in a small line under it, its latest point in the accent. Where a number is the
  * whole story, this says it better than a chart.
  *
  * {{{
  *   val spend = StatTile("Spend this week", ChartFormat.money("$")(week()), Some(0.12), "vs last week")
  *   spend.upIsGood @= false
  *   spend.trend := days().map(_.spend)
  * }}} */
class StatTile extends Container {
  override def kind: String = "StatTile"

  val label: Var[String] = own(Var(""))
  val value: Var[String] = own(Var(""))
  val delta: Var[Option[Double]] = own(Var(None))
  val deltaFormat: Var[Double => String] = own(Var((d: Double) => ChartFormat.percent(d, 1)))
  val period: Var[String] = own(Var(""))
  /** Whether a rise is good news (sales), rather than bad (spend, errors). */
  val upIsGood: Var[Boolean] = own(Var(true))
  val trend: Var[Vector[Double]] = own(Var(Vector.empty))

  private def moved: Double = delta().getOrElse(0.0)
  private def good: Boolean = (moved > 0.0) == upIsGood()
  private def tone: Color = if (moved == 0.0) Theme.muted() else if (good) Theme.successText() else Theme.dangerText()
  private def deltaWords: String = deltaFormat()(math.abs(moved))

  private val name = Chart.words(TextView.bound(label(), 13.0, Theme.muted()))
  private val big = new TextView {
    direction @= everyui.component.types.TextDirection.Auto
    font.size @= 30.0
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
    content := value()
  }
  layout @= Some(VerticalLayout(4.0))
  size.height := contentHeight()
  children ++= List(name, big)

  // the move, made the first time there is one
  private var change: Option[Container] = None
  delta.attachAndFire(d => if (d.nonEmpty && change.isEmpty) {
    val arrow = new MaterialIconView(MaterialIcon.ArrowUpward, 16.0)
    arrow.icon := (if (moved < 0.0) MaterialIcon.ArrowDownward else MaterialIcon.ArrowUpward)
    arrow.visible := moved != 0.0
    arrow.color := tone
    val amount = Chart.words(TextView.bound(deltaWords, 13.0, tone))
    amount.font.weight @= FontWeight.SemiBold
    val against = Chart.words(TextView.bound(period(), 12.0, Theme.muted()))
    val row = new Container {
      size.width := StatTile.this.size.width()
      size.height := math.max(arrow.size.height(), amount.size.height())
    }
    Mirror.place(arrow, () => row.size.width(), () => 0.0)
    Mirror.place(amount, () => row.size.width(), () => if (moved != 0.0) arrow.size.width() + 2.0 else 0.0)
    Mirror.place(against, () => row.size.width(), () => (if (moved != 0.0) arrow.size.width() + 2.0 else 0.0) +
      amount.size.width() + 6.0)
    List(arrow, amount, against).foreach(t => t.position.top := (row.size.height() - t.size.height()) / 2.0)
    row.children ++= List(arrow, amount, against)
    row.visible := delta().nonEmpty
    row.collapses @= true
    change = Some(row)
    children @= (children().take(2) :+ row) ++ children().drop(2)
  })

  // the trend, made the first time there is one
  private var spark: Option[Drawing] = None
  trend.attachAndFire(t => if (t.size > 1 && spark.isEmpty) {
    val d = new Drawing
    d.size.width := size.width()
    d.size.height @= 28.0
    d.figures := StatTile.trendFigures(trend(), d.size.width(), d.size.height())
    d.visible := trend().size > 1
    d.collapses @= true
    spark = Some(d)
    children += d
  })

  semantics := {
    val moves = delta() match {
      case Some(d) if d > 0.0 => ", " + ChartWords.up(deltaWords) + (if (period().nonEmpty) " " + period() else "")
      case Some(d) if d < 0.0 => ", " + ChartWords.down(deltaWords) + (if (period().nonEmpty) " " + period() else "")
      case _ => ""
    }
    Semantics("group", label = label() + ": " + value() + moves)
  }
}

object StatTile {
  def apply(label: => String, value: => String, delta: => Option[Double] = None, period: => String = ""): StatTile = {
    val tile = new StatTile
    tile.label := label
    tile.value := value
    tile.delta := delta
    tile.period := period
    tile
  }

  /** A trend `across` by `down`: a line in the de-emphasis grey with its latest point a dot in the accent; where
    * curves are not drawn, a small bar for each value, the latest in the accent. */
  def trendFigures(values: Vector[Double], across: Double, down: Double): List[Figure] =
    if (values.size < 2 || across <= 0.0) Nil
    else if (ChartPaths.drawn) {
      val quiet = ChartTone.Muted.colour(0)
      val line = Sparkline.figures(values, across - 8.0, down - 6.0, quiet, 1.5).map(f =>
        f.copy(path = f.path.placed(1.0, 0.0, 3.0)))
      val low = values.min
      val span = math.max(values.max - low, 1e-9)
      val x = across - 8.0 - 0.75
      val y = 3.0 + 0.75 + (down - 6.0 - 1.5) * (1.0 - (values.last - low) / span)
      line ++ List(Figure(Path.circle(x, y, 3.5), fill = Some(Theme.accent())))
    }
    else {
      val low = math.min(0.0, values.min)
      val span = math.max(values.max - low, 1e-9)
      val step = across / values.size
      values.indices.toList.map { i =>
        val h = math.max(1.0, down * (values(i) - low) / span)
        Figure(Path.rect(i * step, down - h, math.max(1.0, step - 2.0), h),
          fill = Some(if (i == values.size - 1) Theme.accent() else ChartTone.Muted.colour(0)))
      }
    }
}
