package everyui.chart

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.component.types.{FontWeight, SemanticRange, Semantics}
import everyui.draw.{Drawing, Figure}
import everyui.theme.Theme
import reactify.*

/** A small dial: a half ring from `min` at the left to `max` at the right, lit as far as `value`, the value written
  * large under its middle and its `label` under that. Like a `Meter` it turns the palette's warning or danger past
  * `warnAt` or `dangerAt` (shares of the way; 0 for never), and its unlit part is a lighter step of the lit one's
  * colour. It is as wide as it is given (`size.width`) and half that tall, with its words. A Roku and an Apple TV draw
  * no curves: there the dial is a bar.
  *
  * {{{
  *   val load = Gauge("Context used", used(), 200000.0, ChartFormat.compact)
  *   load.dangerAt @= 0.9
  *   load.size.width @= 160.0
  * }}} */
class Gauge extends Container {
  override def kind: String = "Gauge"

  val label: Var[String] = own(Var(""))
  val value: Var[Double] = own(Var(0.0))
  val min: Var[Double] = own(Var(0.0))
  val max: Var[Double] = own(Var(1.0))
  val format: Var[Double => String] = own(Var(ChartFormat.compact))
  val tone: Var[ChartTone] = own(Var(ChartTone.Accent))
  val warnAt: Var[Double] = own(Var(0.0))
  val dangerAt: Var[Double] = own(Var(0.0))

  private val eased = new Eased(this)
  value.attachAndFire(v => eased.to(Vector(Vector(v))))

  /** How far along the dial `v` is, 0 to 1. */
  def shareOf(v: Double): Double = {
    val span = max() - min()
    if (span <= 0.0) 0.0 else math.max(0.0, math.min(1.0, (v - min()) / span))
  }
  private def drawnShare: Double = shareOf(eased.now().headOption.flatMap(_.headOption).getOrElse(min()))
  private def fill: Color = {
    val f = shareOf(value())
    if (dangerAt() > 0.0 && f >= dangerAt()) Theme.danger()
    else if (warnAt() > 0.0 && f >= warnAt()) Theme.warning()
    else tone().colour(0)
  }

  private val dial = new Drawing
  dial.size.width := size.width()
  dial.size.height := (if (ChartPaths.drawn) size.width() / 2.0 else 10.0)
  dial.figures := {
    val w = dial.size.width()
    val f = drawnShare
    val c = fill
    if (w <= 0.0) Nil
    else if (!ChartPaths.drawn) List(Figure(ChartPaths.bar(0.0, 0.0, w, 10.0, ""), fill = Some(c.blend(Theme.surface(), 0.78))),
      Figure(ChartPaths.bar(if (everyui.i18n.Direction.rtl()) w - w * f else 0.0, 0.0, w * f, 10.0, ""), fill = Some(c)))
    else {
      val r = w / 2.0
      val thick = math.max(6.0, r * 0.22)
      val start = -math.Pi / 2.0
      List(Figure(ChartPaths.slice(r, r, r - thick, r, start, math.Pi / 2.0), fill = Some(c.blend(Theme.surface(), 0.78))),
        if (everyui.i18n.Direction.rtl()) Figure(ChartPaths.slice(r, r, r - thick, r, math.Pi / 2.0 - math.Pi * f, math.Pi / 2.0),
          fill = Some(c))
        else Figure(ChartPaths.slice(r, r, r - thick, r, start, start + math.Pi * f), fill = Some(c)))
    }
  }
  private val big = new TextView {
    direction @= everyui.component.types.TextDirection.Auto
    font.size := math.max(16.0, math.min(28.0, Gauge.this.size.width() * 0.16))
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
    content := format()(value())
  }
  private val name = Chart.words(TextView.bound(label(), 12.0, Theme.muted()))
  big.position.left := (size.width() - big.size.width()) / 2.0
  big.position.top := dial.size.height() - (if (ChartPaths.drawn) big.size.height() else -6.0)
  name.position.left := (size.width() - name.size.width()) / 2.0
  name.position.top := big.position.top() + big.size.height() + 2.0
  size.height := name.position.top() + name.size.height()
  children ++= List(dial, big, name)
  semantics := {
    val percent = math.round(shareOf(value()) * 100.0).toDouble
    Semantics("progressbar", label = label(), range = Some(SemanticRange(percent, 0.0, 100.0,
      ChartWords.of(format()(value()), format()(max())))))
  }
}

object Gauge {
  def apply(label: => String, value: => Double, max: => Double, format: Double => String = ChartFormat.compact): Gauge = {
    val gauge = new Gauge
    gauge.label := label
    gauge.value := value
    gauge.max := max
    gauge.format @= format
    gauge
  }
}
