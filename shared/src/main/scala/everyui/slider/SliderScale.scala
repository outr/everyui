package everyui.slider

import everyui.event.{Key, KeyEvent}

/** The numbers a slider runs over: `min` to `max`, in steps of `step`, or else in `divisions` equal steps; with
  * neither, smoothly. Every value a slider sets comes through here, snapped to a step and kept in range. */
case class SliderScale(min: Double, max: Double, step: Double = 0.0, divisions: Option[Int] = None) {
  /** How far apart the values are: `step`, else the range over `divisions`; 0 where they run smoothly. */
  val grain: Double =
    if (step > 0.0) step else divisions.filter(_ > 0).map(n => (max - min) / n.toDouble).getOrElse(0.0)
  // a step that goes into 1 a whole number of times (0.1, 0.25) is counted in by dividing, so 3 steps of 0.1 come to 0.3
  // rather than 0.30000000000000004
  private val per: Double = {
    val inverse = if (grain > 0.0) 1.0 / grain else 0.0
    val whole = math.floor(inverse + 0.5)
    if (grain > 0.0 && grain < 1.0 && math.abs(inverse - whole) < 1e-9) whole else 0.0
  }

  def span: Double = max - min
  def clamp(v: Double): Double = math.max(min, math.min(max, v))
  /** `v` at the nearest step, in range; `max` is one too, where the steps do not reach it. */
  def snap(v: Double): Double =
    if (grain <= 0.0) clamp(v)
    else {
      val near = clamp(at(math.floor((v - min) / grain + 0.5)))
      if (math.abs(max - v) < math.abs(near - v)) max else near
    }
  /** The highest step at or under `v`, in range. */
  def below(v: Double): Double = if (grain <= 0.0) clamp(v) else clamp(at(math.floor((v - min) / grain + 1e-9)))
  /** The lowest step at or over `v`, in range. */
  def above(v: Double): Double = if (grain <= 0.0) clamp(v) else clamp(at(math.ceil((v - min) / grain - 1e-9)))
  private def at(steps: Double): Double = if (per > 0.0) min + steps / per else min + steps * grain

  /** How far along `v` is, 0 at `min` and 1 at `max`. */
  def fraction(v: Double): Double = if (span <= 0.0) 0.0 else (clamp(v) - min) / span
  /** The value `fraction` of the way along, snapped. */
  def value(fraction: Double): Double = snap(min + math.max(0.0, math.min(1.0, fraction)) * span)

  /** How far an arrow moves: a step, or a hundredth of the range where it runs smoothly. */
  def keyStep: Double = if (grain > 0.0) grain else span / 100.0
  /** How far Page Up and Page Down move: a tenth of the range, in whole steps, one at least. */
  def pageStep: Double =
    if (grain > 0.0) math.max(1.0, math.floor(span / 10.0 / grain + 0.5)) * grain else span / 10.0

  /** Where the ticks are, as fractions of the way along: one at each division, and none without divisions. */
  def ticks: List[Double] = divisions.filter(_ > 0) match {
    case Some(n) =>
      var all = List.empty[Double]
      var i = n
      while (i >= 0) {
        all = (i.toDouble / n.toDouble) :: all
        i -= 1
      }
      all
    case None => Nil
  }

  /** Where `e` moves a value from `now`, or None for a key a slider leaves alone: the arrows along it by a step (Left
    * and Right on a horizontal one, mirrored in a right-to-left language as the slider is; Up and Down on a vertical
    * one), Page Up and Page Down by a tenth, Home and End to its ends. The arrows across it are left to the focus. */
  def key(e: KeyEvent, now: Double, vertical: Boolean, rtl: Boolean): Option[Double] = {
    val forward = if (rtl) -1.0 else 1.0
    val by: Option[Double] =
      if (!vertical && e.key == Key.ArrowRight) Some(keyStep * forward)
      else if (!vertical && e.key == Key.ArrowLeft) Some(-keyStep * forward)
      else if (vertical && e.key == Key.ArrowUp) Some(keyStep)
      else if (vertical && e.key == Key.ArrowDown) Some(-keyStep)
      else if (e.key == Key.PageUp) Some(pageStep)
      else if (e.key == Key.PageDown) Some(-pageStep)
      else None
    by match {
      case Some(d) => Some(snap(now + d))
      case None =>
        if (e.name == "Home") Some(min)
        else if (e.name == "End") Some(max)
        else None
    }
  }

  /** A range's start set to `v`: snapped, and kept `gap` under `end`. */
  def start(v: Double, end: Double, gap: Double): Double = math.min(snap(v), below(end - gap))
  /** A range's end set to `v`: snapped, and kept `gap` over `start`. */
  def end(v: Double, start: Double, gap: Double): Double = math.max(snap(v), above(start + gap))
}

object SliderScale {
  /** A number as a slider shows it by default: to two places at most, with no trailing zeros ("3", "0.25", "-1.5"). */
  def text(v: Double): String = {
    val hundredths = math.floor(math.abs(v) * 100.0 + 0.5)
    val whole = math.floor(hundredths / 100.0)
    val rest = (hundredths - whole * 100.0).toInt
    val sign = if (v < 0.0 && hundredths > 0.0) "-" else ""
    val tail =
      if (rest == 0) ""
      else if (rest % 10 == 0) "." + (rest / 10).toString
      else if (rest < 10) ".0" + rest.toString
      else "." + rest.toString
    sign + whole.toLong.toString + tail
  }
}
