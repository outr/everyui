package everyui.chart

/** Values from `low` to `high` laid along pixels from `start` to `end`: a chart's value axis. `end` may be less than
  * `start`, as a value axis runs up the screen while its pixels run down.
  *
  * {{{
  *   val y = LinearScale.nice(0.0, 87.0, 5).placed(200.0, 0.0)   // 0 to 100 up 200 pixels, ticks of 20
  *   y(50.0)                                                     // 100.0
  * }}} */
case class LinearScale(low: Double, high: Double, step: Double, start: Double = 0.0, end: Double = 1.0) {
  /** Where `v` falls. */
  def apply(v: Double): Double = {
    val span = high - low
    if (span == 0.0) start else start + (v - low) / span * (end - start)
  }

  /** The value at pixel `p`. */
  def invert(p: Double): Double = if (end == start) low else low + (p - start) / (end - start) * (high - low)

  /** The same values laid along other pixels. */
  def placed(start: Double, end: Double): LinearScale = copy(start = start, end = end)

  /** The ticks from `low` to `high`, a `step` apart, each rounded to the step's own places, so 0.30000000000000004 is
    * 0.3 and a tick at nothing is 0 and never -0. */
  def ticks: Vector[Double] =
    if (step <= 0.0) Vector(low)
    else {
      val n = math.round((high - low) / step).toInt
      (0 to n).toVector.map(i => LinearScale.clean(low + i * step, step))
    }
}

object LinearScale {
  /** A scale over `low` to `high` widened to round ticks, about `count` of them (Heckbert's nice numbers): each step 1,
    * 2 or 5 times a power of ten, the ends on whole steps. A span of nothing is widened round its value (0 to 1 round
    * nothing). */
  def nice(low: Double, high: Double, count: Int = 5): LinearScale = {
    val (a, b) = if (low <= high) (low, high) else (high, low)
    val (from, to) =
      if (b > a) (a, b)
      else if (a == 0.0) (0.0, 1.0)
      else if (a > 0.0) (0.0, a)
      else (a, 0.0)
    val step = niceNumber(niceNumber(to - from, round = false) / math.max(1, count - 1), round = true)
    val first = math.floor(from / step + 1e-9) * step
    val last = math.ceil(to / step - 1e-9) * step
    LinearScale(clean(first, step), clean(last, step), step)
  }

  /** A 1, 2, 5 or 10 times a power of ten near `x`: the nearest where `round`, else the least not under it. */
  def niceNumber(x: Double, round: Boolean): Double =
    if (x <= 0.0) 1.0
    else {
      val power = math.pow(10.0, math.floor(math.log(x) / math.log(10.0) + 1e-12))
      val f = x / power
      val nice =
        if (round) { if (f < 1.5) 1.0 else if (f < 3.0) 2.0 else if (f < 7.0) 5.0 else 10.0 }
        else { if (f <= 1.0 + 1e-9) 1.0 else if (f <= 2.0 + 1e-9) 2.0 else if (f <= 5.0 + 1e-9) 5.0 else 10.0 }
      nice * power
    }

  /** `v` rounded to a hundredth of `step`, which drops what floating point adds (0.1 + 0.2), and 0 for -0. */
  def clean(v: Double, step: Double): Double = {
    val unit = if (step > 0.0) step / 100.0 else 1e-9
    val r = math.round(v / unit).toDouble * unit
    // the step's own places: a step of 0.1 is written to one, so 0.30000000000000004 reads 0.3
    val places = math.max(0, math.min(12, math.ceil(-math.log(unit) / math.log(10.0) - 1e-9).toInt))
    val scale = math.pow(10.0, places)
    val out = math.round(r * scale).toDouble / scale
    if (out == 0.0) 0.0 else out
  }
}
