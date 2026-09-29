package everyui.slider

import everyui.component.Container
import everyui.component.types.{SemanticRange, Semantics}
import everyui.event.{KeyEvent, PointerDrag}
import everyui.i18n.Direction
import reactify.*

/** A value set along a track, as Material's slider: `value` runs from `min` to `max` in steps of `step` (0 runs
  * smoothly), or in `divisions` equal steps with a tick at each. `length` long (bind `size.width`, or `size.height`
  * on a `vertical` one, to change it) and 40 across.
  *
  * A press on the track puts the thumb there and a drag takes it along, the thumb held until the pointer or finger lets
  * go wherever it goes. With the focus, the arrows along it move it a step, Page Up and Page Down a tenth, Home and End
  * to its ends; the arrows across it move the focus on, so on a remote Left and Right set it and Up and Down leave. A
  * horizontal one runs from the right in a right-to-left language, and its arrows with it. `value` changes as it
  * moves; `onChangeEnd` hears the value it came to once a drag lets go, and after each key, for work to do only then
  * (a filter). `showValue` says when the value shows over the thumb, in `format`'s words, which a screen reader
  * hears too, with `label`. `disabled` puts it out of use: faded, and the focus passes it by.
  *
  * {{{
  * val volume = Var(40.0)
  * val slider = new Slider(volume, 0.0, 100.0, step = 1.0, label = "Volume", showValue = ValueLabel.Moving,
  *   format = v => SliderScale.text(v) + "%")
  * slider.onChangeEnd.attach(v => save(v))
  * }}} */
class Slider(val value: Var[Double], min: Double = 0.0, max: Double = 1.0, step: Double = 0.0,
             divisions: Option[Int] = None, length: Double = Slider.length, label: String = "",
             showValue: ValueLabel = ValueLabel.Never, format: Double => String = SliderScale.text,
             vertical: Boolean = false) extends Container {
  val range: SliderScale = SliderScale(min, max, step, divisions)
  /** The value a change came to once it is done: a drag let go, a key pressed. */
  val onChangeEnd: Channel[Double] = Channel[Double]

  // set from the keys since the focus came, which shows the value where it shows while moving
  private val keyed: Var[Boolean] = own(Var(false))
  private val dragged: Var[Boolean] = own(Var(false))
  private val track = new SliderTrack(range, vertical, range.min, value(), !disabled())
  private val thumb = new SliderThumb(SliderTrack.thumb(!disabled()), dragged(), focused(), !disabled(), bubble, vertical)

  focusable := !disabled()
  if (vertical) {
    size.width @= Slider.thickness
    size.height @= length
  }
  else {
    size.width @= length
    size.height @= Slider.thickness
  }
  track.size.width := size.width()
  track.size.height := size.height()
  if (vertical) {
    thumb.position.left := (size.width() - SliderThumb.halo) / 2.0
    thumb.position.top := track.offset(value()) - SliderThumb.halo / 2.0
  }
  else {
    thumb.position.left := track.offset(value()) - SliderThumb.halo / 2.0
    thumb.position.top := (size.height() - SliderThumb.halo) / 2.0
  }
  semantics := Semantics.slider(label, SemanticRange(value(), range.min, range.max, format(value())))
  children ++= List(track, thumb)
  focused.attach(f => if (!f) keyed @= false)

  private val drag = new PointerDrag(this, (x, y) => value @= track.valueAt(x, y), () => {
    dragged @= false
    onChangeEnd @= value()
  }, (_, _) => {
    val take = !disabled()
    if (take) {
      dragged @= true
      keyed @= false
    }
    take
  })

  event.key.down.attach { e =>
    range.key(e, value(), vertical, Direction.rtl()).foreach { v =>
      keyed @= true
      value @= v
      onChangeEnd @= v
    }
  }

  override def keepsEvent(e: KeyEvent): Boolean = !disabled() && range.key(e, value(), vertical, Direction.rtl()).nonEmpty

  private def bubble: Option[String] = showValue match {
    case ValueLabel.Always => Some(format(value()))
    case ValueLabel.Moving if dragged() || keyed() => Some(format(value()))
    case _ => None
  }

  override def kind: String = "Slider(" + label + ")"
}

object Slider {
  /** How long a slider is unless it says. */
  val length: Double = 240.0
  /** How thick a slider's box is: a thumb's halo. */
  val thickness: Double = 40.0
}
