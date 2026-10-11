package everyui.audio

/** A second-order filter, as Robert Bristow-Johnson's Audio EQ Cookbook designs it (the formulas a browser's
  * BiquadFilterNode uses too), run on each of `channels` with its own memory: a high-pass ([[highPass]]) or a peak
  * or dip about a frequency ([[peaking]]). The coefficients are shared and may be set again as it runs; each channel
  * is run in direct form I, which takes a change of coefficients without a burst. Not thread safe. Written as loops
  * over indices, for the Roku's compiler.
  *
  * {{{
  *   val presence = new Biquad(2)
  *   presence.peaking(48000, 2800.0, 1.0, 6.0)
  *   val y = presence.run(x, 0)
  * }}} */
final class Biquad(val channels: Int) {
  // normalised by a0
  private var b0: Double = 1.0
  private var b1: Double = 0.0
  private var b2: Double = 0.0
  private var a1: Double = 0.0
  private var a2: Double = 0.0
  // each channel's last two in and out
  private val x1: Array[Double] = new Array[Double](channels)
  private val x2: Array[Double] = new Array[Double](channels)
  private val y1: Array[Double] = new Array[Double](channels)
  private val y2: Array[Double] = new Array[Double](channels)

  /** A high-pass at `hz` with resonance `q` (1/sqrt(2) is Butterworth's: -3 dB at `hz`, then 12 dB an octave down). */
  def highPass(rate: Int, hz: Double, q: Double): Unit = {
    val w = 2.0 * math.Pi * hz / rate
    val cos = math.cos(w)
    val alpha = math.sin(w) / (2.0 * q)
    val a0 = 1.0 + alpha
    b0 = (1.0 + cos) / 2.0 / a0
    b1 = -(1.0 + cos) / a0
    b2 = (1.0 + cos) / 2.0 / a0
    a1 = -2.0 * cos / a0
    a2 = (1.0 - alpha) / a0
  }

  /** A peak (or, with `db` below 0, a dip) of `db` at `hz`, as broad as `q` says (1 is about an octave and a third
    * between the points of half its gain in dB). At 0 dB it changes nothing. */
  def peaking(rate: Int, hz: Double, q: Double, db: Double): Unit = {
    val a = math.pow(10.0, db / 40.0)
    val w = 2.0 * math.Pi * hz / rate
    val cos = math.cos(w)
    val alpha = math.sin(w) / (2.0 * q)
    val a0 = 1.0 + alpha / a
    b0 = (1.0 + alpha * a) / a0
    b1 = -2.0 * cos / a0
    b2 = (1.0 - alpha * a) / a0
    a1 = -2.0 * cos / a0
    a2 = (1.0 - alpha / a) / a0
  }

  /** `x` through channel `c`'s filter. */
  def run(x: Double, c: Int): Double = {
    val y = b0 * x + b1 * x1(c) + b2 * x2(c) - a1 * y1(c) - a2 * y2(c)
    x2(c) = x1(c)
    x1(c) = x
    y2(c) = y1(c)
    y1(c) = y
    y
  }

  /** Every channel's memory forgotten, as if it had heard only silence. */
  def clear(): Unit = {
    var c = 0
    while (c < channels) {
      x1(c) = 0.0
      x2(c) = 0.0
      y1(c) = 0.0
      y2(c) = 0.0
      c += 1
    }
  }

  /** What it does to a sine at `hz`, in dB, from its coefficients now. */
  def response(rate: Int, hz: Double): Double = {
    val w = 2.0 * math.Pi * hz / rate
    val c1 = math.cos(w)
    val s1 = math.sin(w)
    val c2 = math.cos(2.0 * w)
    val s2 = math.sin(2.0 * w)
    // b0 + b1 e^-jw + b2 e^-2jw over 1 + a1 e^-jw + a2 e^-2jw
    val nr = b0 + b1 * c1 + b2 * c2
    val ni = -(b1 * s1 + b2 * s2)
    val dr = 1.0 + a1 * c1 + a2 * c2
    val di = -(a1 * s1 + a2 * s2)
    10.0 * math.log10((nr * nr + ni * ni) / (dr * dr + di * di))
  }
}
