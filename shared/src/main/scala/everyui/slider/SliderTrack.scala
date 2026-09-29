package everyui.slider

import everyui.Color
import everyui.component.Container
import everyui.i18n.Direction
import everyui.theme.Theme

/** A slider's track, as big as the slider: a rail along its middle, lit from `from` to `to` (values), with a tick at
  * each of the scale's divisions, dark on the lit part and light off it. The rail stops short of the box's ends by
  * a thumb's halo, so a thumb at either end stays inside. A horizontal one runs from the right in a right-to-left
  * language; a vertical one runs up. Size it; `offset` and `valueAt` are where a value sits along it and the value at
  * a point, for the thumbs and the pointer. */
class SliderTrack(steps: SliderScale, vertical: Boolean, from: => Double, to: => Double, enabled: => Boolean)
  extends Container {
  private val rail = new Container
  private val band = new Container
  private def along: Double = if (vertical) size.height() else size.width()
  private def across: Double = if (vertical) size.width() else size.height()

  /** How far the rail runs. */
  def span: Double = math.max(0.0, along - SliderTrack.inset * 2.0)

  /** The middle of a thumb at `v`, from the box's left (or, vertical, its top). */
  def offset(v: Double): Double = {
    val f = steps.fraction(v)
    SliderTrack.inset + (if (vertical || Direction.rtl()) 1.0 - f else f) * span
  }

  /** The value at `x`, `y` in the box, snapped. */
  def valueAt(x: Double, y: Double): Double = {
    val f = if (span <= 0.0) 0.0 else ((if (vertical) y else x) - SliderTrack.inset) / span
    steps.value(if (vertical || Direction.rtl()) 1.0 - f else f)
  }

  List(rail, band).foreach { bar =>
    bar.radius @= SliderTrack.rail / 2.0
    if (vertical) {
      bar.size.width @= SliderTrack.rail
      bar.position.left := (across - SliderTrack.rail) / 2.0
    }
    else {
      bar.size.height @= SliderTrack.rail
      bar.position.top := (across - SliderTrack.rail) / 2.0
    }
  }
  if (vertical) {
    rail.position.top @= SliderTrack.inset
    rail.size.height := span
    band.position.top := math.min(offset(from), offset(to))
    band.size.height := math.abs(offset(to) - offset(from))
  }
  else {
    rail.position.left @= SliderTrack.inset
    rail.size.width := span
    band.position.left := math.min(offset(from), offset(to))
    band.size.width := math.abs(offset(to) - offset(from))
  }
  rail.backgroundColor := SliderTrack.inactive(enabled)
  band.backgroundColor := SliderTrack.active(enabled)
  children ++= List(rail, band)
  private val ticks = steps.ticks
  ticks.foreach(f => children += tick(f))

  private def tick(f: Double): Container = {
    val dot = new Container
    val v = steps.min + f * steps.span
    dot.size.width @= SliderTrack.tick
    dot.size.height @= SliderTrack.tick
    dot.radius @= SliderTrack.tick / 2.0
    if (vertical) {
      dot.position.left := (across - SliderTrack.tick) / 2.0
      dot.position.top := offset(v) - SliderTrack.tick / 2.0
    }
    else {
      dot.position.left := offset(v) - SliderTrack.tick / 2.0
      dot.position.top := (across - SliderTrack.tick) / 2.0
    }
    // hidden when there is less than a tick's width either side of it
    dot.visible := span / math.max(1.0, ticks.size.toDouble - 1.0) >= SliderTrack.tick * 3.0
    dot.backgroundColor := {
      val lit = v >= math.min(from, to) - 1e-9 && v <= math.max(from, to) + 1e-9
      if (!enabled) Theme.text().withAlpha(if (lit) 0.54 else 0.38)
      else if (lit) Theme.onAccent().withAlpha(0.7)
      else Theme.accent().withAlpha(0.7)
    }
    dot
  }

  override def kind: String = "SliderTrack"
}

object SliderTrack {
  /** How far the rail stops short of each end: a thumb's halo, so a thumb at an end stays in the box. */
  val inset: Double = 20.0
  /** How thick the rail is. */
  val rail: Double = 4.0
  /** How big a tick is. */
  val tick: Double = 2.0

  /** The lit part of the rail, and a thumb: the accent, or the text colour faded where the slider is out of use. */
  def active(enabled: Boolean): Color = if (enabled) Theme.accent() else Theme.text().withAlpha(0.38)
  /** The rest of the rail. */
  def inactive(enabled: Boolean): Color = if (enabled) Theme.accent().withAlpha(0.26) else Theme.text().withAlpha(0.12)
  /** A thumb, solid even out of use, so the rail does not show through it. */
  def thumb(enabled: Boolean): Color = if (enabled) Theme.accent() else Theme.background().blend(Theme.text(), 0.38)
}
