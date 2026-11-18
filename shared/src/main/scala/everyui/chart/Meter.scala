package everyui.chart

import everyui.Color
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.{SemanticRange, Semantics}
import everyui.font.MaterialIcon
import everyui.kit.Mirror
import everyui.theme.Theme
import reactify.*

/** A labelled progress bar: how far `value` is toward `max`, its `label` at the start and the amount at the end ("$620
  * of $1,000"), over a track filled that far. The track is a lighter step of the fill's own colour, so the state reads
  * across the whole bar. The fill is the tone's (the accent by default) until it passes `warnAt` or `dangerAt` (shares
  * of `max`; 0 for never), where it turns the palette's warning or danger, a mark beside the amount saying so too.
  * It fills the width it is given (`size.width`), and eases to a new value unless motion is reduced. A screen reader
  * hears it as a progress bar named by its label, at its amount.
  *
  * {{{
  *   val budget = Meter("Monthly budget", spent(), 1000.0, ChartFormat.money("$", 0))
  *   budget.warnAt @= 0.8
  *   budget.dangerAt @= 1.0
  *   budget.size.width @= 320.0
  * }}} */
class Meter extends Container {
  override def kind: String = "Meter"

  val label: Var[String] = own(Var(""))
  val value: Var[Double] = own(Var(0.0))
  val max: Var[Double] = own(Var(1.0))
  val format: Var[Double => String] = own(Var(ChartFormat.compact))
  val tone: Var[ChartTone] = own(Var(ChartTone.Accent))
  /** The share of `max` past which the fill is the palette's warning; 0 for never. */
  val warnAt: Var[Double] = own(Var(0.0))
  /** The share of `max` past which it is the palette's danger; 0 for never. */
  val dangerAt: Var[Double] = own(Var(0.0))
  /** How thick the bar is. */
  val thickness: Var[Double] = own(Var(8.0))

  private val eased = new Eased(this)
  value.attachAndFire(v => eased.to(Vector(Vector(v))))

  /** How far along it is, 0 to 1, as drawn. */
  def fraction: Double = {
    val m = max()
    val v = eased.now().headOption.flatMap(_.headOption).getOrElse(0.0)
    if (m <= 0.0) 0.0 else math.max(0.0, math.min(1.0, v / m))
  }

  /** "warning", "danger" or "" for the value as it is. */
  def state: String = {
    val f = if (max() <= 0.0) 0.0 else value() / max()
    if (dangerAt() > 0.0 && f >= dangerAt()) "danger" else if (warnAt() > 0.0 && f >= warnAt()) "warning" else ""
  }

  private def fill: Color = state match {
    case "danger" => Theme.danger()
    case "warning" => Theme.warning()
    case _ => tone().colour(0)
  }

  private val name = Chart.words(TextView.bound(label(), 13.0, Theme.text()))
  private val amount = Chart.words(TextView.bound(ChartWords.of(format()(value()), format()(max())), 13.0, Theme.muted()))
  private val track = new Container {
    size.width := Meter.this.size.width()
    size.height := thickness()
    radius := thickness() / 2.0
    backgroundColor := fill.blend(Theme.surface(), 0.78)
    position.top := name.size.height() + 6.0
  }
  private val done = new Container {
    size.width := track.size.width() * fraction
    size.height := thickness()
    radius := thickness() / 2.0
    backgroundColor := fill
  }
  Mirror.place(done, () => track.size.width(), () => 0.0)
  track.children += done
  Mirror.place(name, () => size.width(), () => 0.0)
  Mirror.place(amount, () => size.width(), () => size.width() - amount.size.width())
  // the mark of a state, made the first time there is one, before the amount
  private var mark: Option[MaterialIconView] = None
  own(Val(state)).attachAndFire(s => if (s.nonEmpty && mark.isEmpty) {
    val m = new MaterialIconView(MaterialIcon.Warning, 15.0)
    m.icon := (if (state == "danger") MaterialIcon.Error else MaterialIcon.Warning)
    m.color := (if (state == "danger") Theme.dangerText() else Theme.warningText())
    m.visible := state.nonEmpty
    Mirror.place(m, () => size.width(), () => size.width() - amount.size.width() - m.size.width() - 4.0)
    m.position.top := (amount.size.height() - m.size.height()) / 2.0
    mark = Some(m)
    children += m
  })
  size.height := track.position.top() + thickness()
  children ++= List(name, amount, track)
  semantics := {
    val m = max()
    val percent = if (m <= 0.0) 0.0 else math.round(math.max(0.0, math.min(1.0, value() / m)) * 100.0).toDouble
    Semantics("progressbar", label = label(), range = Some(SemanticRange(percent, 0.0, 100.0,
      ChartWords.of(format()(value()), format()(m)))))
  }
}

object Meter {
  def apply(label: => String, value: => Double, max: => Double, format: Double => String = ChartFormat.compact): Meter = {
    val meter = new Meter
    meter.label := label
    meter.value := value
    meter.max := max
    meter.format @= format
    meter
  }
}
