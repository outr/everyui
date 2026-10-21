package everyui.slider

import everyui.component.Container
import everyui.component.types.{SemanticRange, Semantics}
import everyui.event.{Key, KeyEvent, PointerDrag}
import everyui.i18n.Direction
import reactify.*

/** A range set along a track, as Material's range slider: two thumbs, `start` and `end`, from `min` to `max` in steps
  * of `step` (0 runs smoothly) or in `divisions` equal steps with a tick at each, the track lit between them. They
  * never cross, and stay `minGap` apart. `length` long (bind `size.width`, or `size.height` on a `vertical` one) and
  * 40 across.
  *
  * A press on the track takes the nearer thumb there and a drag takes it along. Each thumb takes the focus: the arrows
  * along the track move it a step, Page Up and Page Down a tenth, Home and End as far as it can go; Tab goes from the
  * start thumb to the end one, and OK from either to the other, which is how a remote gets there, since its Left and
  * Right are the thumb's and its Up and Down leave. A horizontal one runs from the right in a right-to-left language.
  * `start` and `end` change as they move; `onChangeEnd` hears both once a drag lets go, and after each key.
  * `showValue` says when a thumb's value shows over it, in `format`'s words, which a screen reader hears too, with
  * `label`. `disabled` puts it out of use.
  *
  * {{{
  * val low = Var(20.0)
  * val high = Var(80.0)
  * val price = new RangeSlider(low, high, 0.0, 100.0, step = 5.0, minGap = 10.0, label = "Price",
  *   showValue = ValueLabel.Moving)
  * price.onChangeEnd.attach { case (from, to) => filter(from, to) }
  * }}} */
class RangeSlider(val start: Var[Double], val end: Var[Double], min: Double = 0.0, max: Double = 1.0,
                  step: Double = 0.0, divisions: Option[Int] = None, minGap: Double = 0.0,
                  length: Double = Slider.length, label: String = "", showValue: ValueLabel = ValueLabel.Never,
                  format: Double => String = SliderScale.text, vertical: Boolean = false) extends Container {
  val range: SliderScale = SliderScale(min, max, step, divisions)
  /** Where both thumbs came to once a change is done: a drag let go, a key pressed. */
  val onChangeEnd: Channel[(Double, Double)] = Channel[(Double, Double)]

  private val startPressed: Var[Boolean] = own(Var(false))
  private val endPressed: Var[Boolean] = own(Var(false))
  private val startKeyed: Var[Boolean] = own(Var(false))
  private val endKeyed: Var[Boolean] = own(Var(false))
  private val track = new SliderTrack(range, vertical, start(), end(), !disabled())
  /** The thumb that sets `start`. */
  val startThumb: SliderThumb = new SliderThumb(SliderTrack.thumb(!disabled()), startPressed(), false, !disabled(),
    bubble(start(), startPressed(), startKeyed()), vertical, e => keeps(e, true))
  /** The thumb that sets `end`. */
  val endThumb: SliderThumb = new SliderThumb(SliderTrack.thumb(!disabled()), endPressed(), false, !disabled(),
    bubble(end(), endPressed(), endKeyed()), vertical, e => keeps(e, false))

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
  place(startThumb, start())
  place(endThumb, end())
  List(startThumb, endThumb).foreach { t =>
    t.focusable := !disabled()
    t.clickFocus @= true
  }
  startThumb.semantics := Semantics.slider(label, SemanticRange(start(), range.min, end(), format(start())))
  endThumb.semantics := Semantics.slider(label, SemanticRange(end(), start(), range.max, format(end())))
  children ++= List(track, startThumb, endThumb)
  startThumb.focused.attach(f => if (!f) startKeyed @= false)
  endThumb.focused.attach(f => if (!f) endKeyed @= false)
  startThumb.event.key.down.attach(e => key(e, true))
  endThumb.event.key.down.attach(e => key(e, false))

  private val drag = new PointerDrag(this, (x, y) => move(track.valueAt(x, y)), () => {
    startPressed @= false
    endPressed @= false
    onChangeEnd @= ((start(), end()))
  }, (x, y) => press(x, y))

  private def place(thumb: SliderThumb, v: => Double): Unit =
    if (vertical) {
      thumb.position.left := (size.width() - SliderThumb.halo) / 2.0
      thumb.position.top := track.offset(v) - SliderThumb.halo / 2.0
    }
    else {
      thumb.position.left := track.offset(v) - SliderThumb.halo / 2.0
      thumb.position.top := (size.height() - SliderThumb.halo) / 2.0
    }

  /** A press at `x`, `y` takes the nearer thumb; where they sit together, the one that has room to go that way. */
  private def press(x: Double, y: Double): Boolean = !disabled() && {
    val at = if (vertical) y else x
    val v = track.valueAt(x, y)
    val toStart = math.abs(track.offset(start()) - at)
    val toEnd = math.abs(track.offset(end()) - at)
    val first =
      if (toStart < toEnd) true
      else if (toEnd < toStart) false
      else if (v < start()) true
      else if (v > end()) false
      else end() >= range.max
    startPressed @= first
    endPressed @= !first
    startKeyed @= false
    endKeyed @= false
    (if (first) startThumb else endThumb).focus()
    true
  }

  private def move(v: Double): Unit =
    if (startPressed()) start @= range.start(v, end(), minGap)
    else if (endPressed()) end @= range.end(v, start(), minGap)

  private def keeps(e: KeyEvent, first: Boolean): Boolean =
    !disabled() && (e.key == Key.Enter || range.key(e, if (first) start() else end(), vertical, Direction.rtl()).nonEmpty)

  private def key(e: KeyEvent, first: Boolean): Unit =
    if (e.key == Key.Enter) (if (first) endThumb else startThumb).focus()
    else range.key(e, if (first) start() else end(), vertical, Direction.rtl()).foreach { v =>
      if (first) {
        startKeyed @= true
        start @= range.start(v, end(), minGap)
      }
      else {
        endKeyed @= true
        end @= range.end(v, start(), minGap)
      }
      onChangeEnd @= ((start(), end()))
    }

  private def bubble(v: Double, pressed: Boolean, keyed: Boolean): Option[String] = showValue match {
    case ValueLabel.Always => Some(format(v))
    case ValueLabel.Moving if pressed || keyed => Some(format(v))
    case _ => None
  }

  override def kind: String = "RangeSlider(" + label + ")"
}
