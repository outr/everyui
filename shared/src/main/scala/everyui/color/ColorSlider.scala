package everyui.color

import everyui.Color
import everyui.component.Container
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent}
import everyui.theme.Theme

/** One of a picker's sliders, `width` wide: a track through `stops` (over checks, where it runs to see-through), and a
  * thumb at `at` (0 to 1) showing `thumb`'s colour. A press or a drag along it, and Left and Right while it has the
  * focus, move it by `step` (ten times as far with Shift; Home and End to its ends), calling `set` with where it
  * should be. Up and Down leave it, so a remote goes on past it. `label` names it to a screen reader, with `said`, its
  * value in words. */
class ColorSlider(label: String, width: Double, at: => Double, stops: => List[Color], thumb: => Color,
                  said: => String, step: Double = 0.01, checked: Boolean = false)(set: Double => Unit) extends Container {
  private val inset: Double = ColorThumb.diameter / 2.0 + 2.0
  private val trackHeight: Double = 12.0
  private val track = new ColorTrack(stops)
  private val knob = new ColorThumb(thumb)

  focusable @= true
  size.width @= width
  size.height @= ColorSlider.height
  radius @= ColorSlider.height / 2.0
  borderWidth := (if (focused()) 2.0 else 0.0)
  borderColor := Theme.accent()
  semantics := Semantics("slider", label = label + " " + said)

  track.position.left @= inset
  track.position.top @= (ColorSlider.height - trackHeight) / 2.0
  track.size.width @= width - inset * 2.0
  track.size.height @= trackHeight
  track.radius @= 3.0
  if (checked) {
    val checks = new ColorChecker(width - inset * 2.0, trackHeight)
    checks.position.left @= inset
    checks.position.top @= track.position.top()
    children += checks
  }
  knob.position.left := inset + Hsv.unit(at) * (width - inset * 2.0) - ColorThumb.diameter / 2.0
  knob.position.top @= (ColorSlider.height - ColorThumb.diameter) / 2.0
  children ++= List(track, knob)

  private val drag = new ColorDrag(this, (x, _) => set(Hsv.unit((x - inset) / (width - inset * 2.0))))

  event.key.down.attach(e => key(e))

  override def keepsEvent(e: KeyEvent): Boolean =
    e.key == Key.ArrowLeft || e.key == Key.ArrowRight || e.name == "Home" || e.name == "End"

  private def key(e: KeyEvent): Unit = {
    val by = if (e.shift) step * 10.0 else step
    if (e.key == Key.ArrowLeft) set(Hsv.unit(at - by))
    else if (e.key == Key.ArrowRight) set(Hsv.unit(at + by))
    else if (e.name == "Home") set(0.0)
    else if (e.name == "End") set(1.0)
  }

  override def kind: String = "ColorSlider(" + label + ")"
}

object ColorSlider {
  /** How tall a slider is: room for its thumb and the focus ring round it. */
  val height: Double = 28.0
}
