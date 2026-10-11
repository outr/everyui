package everyui.audio

/** The loud made quieter and the quiet louder, so a film can be heard without waking the house: a compressor, as a
  * broadcaster's is. What MediaSupport.nightMode sets, at an amount from 0 (nothing: every sample passes exactly as
  * it came) to 1.
  *
  * It hears the sound as one: the loudest of the channels at each frame, squared and smoothed over
  * [[NightMode.RmsSeconds]] (a root mean square, where a full-scale sine is -3 dBFS), so a burst on one side brings
  * every channel down together and the picture of the sound does not move. That level goes through the static curve
  * ([[NightMode.curve]]): as it was under the threshold, and over it only 1 dB in every `ratio`, through a soft knee
  * [[NightMode.Knee]] dB wide, and is raised by a makeup gain that leaves what is at [[NightMode.Reference]] as it
  * was: so what is quieter than that (dialogue, a room's quiet) comes up, and what is louder (music, explosions) comes
  * down. The gain that asks for is followed in dB, falling with a time constant of [[NightMode.AttackSeconds]] and
  * rising again with one of [[NightMode.ReleaseSeconds]], the makeup with the rest, so a change of amount neither
  * dips nor swells.
  *
  * The amount sets the three together ([[NightMode.threshold]], [[NightMode.ratio]], [[NightMode.makeup]]): at 1 the
  * threshold is -30 dBFS and the ratio 4:1, so a burst at 0 dBFS (of root mean square) comes down 15 dB, dialogue at
  * -27 goes up about 5 and a passage at -45 up 7.5.
  * A change of amount glides over [[Downmix.GlideSeconds]]. The makeup can take a peak past full scale, so
  * [[ListeningAids]] runs it ahead of a limiter. Interleaved samples, `channels` to a frame. Not thread safe. Written
  * as loops over indices, for the Roku's compiler. */
final class NightMode(val channels: Int, val rate: Int) {
  private var wanted: Double = 0.0
  private var now: Double = 0.0
  // the smoothed square of the level, and the gain the curve and the makeup ask for, in dB, as it follows
  private var square: Double = 0.0
  private val glide: Double = 1.0 - math.exp(-1.0 / (rate * Downmix.GlideSeconds))
  private val smoothing: Double = 1.0 - math.exp(-1.0 / (rate * NightMode.RmsSeconds))
  private val attack: Double = 1.0 - math.exp(-1.0 / (rate * NightMode.AttackSeconds))
  private val release: Double = 1.0 - math.exp(-1.0 / (rate * NightMode.ReleaseSeconds))
  private var gain: Double = 0.0
  // since it was last read
  private var lowest: Double = 0.0
  private var highest: Double = 0.0

  /** How strong, 0 to 1 ([[NightMode.clamp]]): heard from the next frame, gliding there. */
  def level(amount: Double): Unit = wanted = NightMode.clamp(amount)

  /** The amount now, after its glide. */
  def amount: Double = now

  /** Whether it is doing nothing: at 0, not gliding anywhere, and its gain given back. */
  def quiet: Boolean = now == 0.0 && wanted == 0.0 && gain == 0.0

  /** What the curve is taking off now, in dB, before the makeup. */
  def reduction: Double = gain - NightMode.makeup(now)

  /** The gain it applies now, the makeup with the reduction, in dB. */
  def gainDb: Double = gain

  /** The least and the most gain it applied since this was last asked, in dB; then counted afresh. */
  def range(): (Double, Double) = {
    val r = (lowest, highest)
    lowest = gain
    highest = gain
    r
  }

  /** `count` frames of `samples` (interleaved), every channel brought up or down together in place; at 0, left
    * exactly as they are. */
  def process(samples: Array[Float], count: Int): Unit = if (!quiet) {
    var f = 0
    while (f < count) {
      if (now != wanted) {
        now += (wanted - now) * glide
        if (math.abs(wanted - now) < 1e-6) now = wanted
      }
      val at = f * channels
      if (now == 0.0 && math.abs(gain) < 0.001) {
        // glided back to nothing and its gain given back: from here on passed by, heard afresh when next asked for
        square = 0.0
        gain = 0.0
      }
      else {
        var loudest = 0.0
        var c = 0
        while (c < channels) {
          val x = samples(at + c).toDouble
          if (x * x > loudest) loudest = x * x
          c += 1
        }
        square += (loudest - square) * smoothing
        val heard = NightMode.decibels(square)
        // the makeup follows with the rest, so a change of amount neither dips nor swells
        val target = NightMode.curve(heard, now) - heard + NightMode.makeup(now)
        gain += (target - gain) * (if (target < gain) attack else release)
        if (gain < lowest) lowest = gain
        if (gain > highest) highest = gain
        val g = math.pow(10.0, gain / 20.0)
        c = 0
        while (c < channels) {
          samples(at + c) = (samples(at + c) * g).toFloat
          c += 1
        }
      }
      f += 1
    }
  }
}

object NightMode {
  /** How long the level is smoothed over: a root mean square of about this much. */
  val RmsSeconds: Double = 0.005
  /** How quickly the gain falls to a burst (to within a third). */
  val AttackSeconds: Double = 0.005
  /** How quickly it comes back after one (to within a third). */
  val ReleaseSeconds: Double = 0.2
  /** How wide the knee about the threshold is, in dB. */
  val Knee: Double = 6.0
  /** The level, in dBFS, the makeup leaves as it was: a little over a film's dialogue (about -27 to -24), so that
    * comes up a few dB and what is quieter more. */
  val Reference: Double = -20.0
  /** The quietest level heard, in dBFS: silence is this. */
  val Floor: Double = -120.0

  /** `amount` within 0 and 1; anything not a number is 0. */
  def clamp(amount: Double): Double = if (amount.isNaN) 0.0 else math.max(0.0, math.min(1.0, amount))

  /** Where it starts to bring the level down, in dBFS: -20 just above 0, to -30 at full. */
  def threshold(amount: Double): Double = -20.0 - 10.0 * amount

  /** How many dB in must rise over the threshold for one more out: 1 (nothing) at 0, to 4 at full. */
  def ratio(amount: Double): Double = 1.0 + 3.0 * amount

  /** The level out for `level` in (both in dBFS), at `amount`: as it was under the knee, a quadratic through the
    * knee, and the threshold plus a `ratio`th of the rest over it. */
  def curve(level: Double, amount: Double): Double = {
    val t = threshold(amount)
    val r = ratio(amount)
    val over = level - t
    if (2.0 * over < -Knee) level
    else if (2.0 * over > Knee) t + over / r
    else level + (1.0 / r - 1.0) * (over + Knee / 2.0) * (over + Knee / 2.0) / (2.0 * Knee)
  }

  /** The gain, in dB, added after the curve: what the curve takes off [[Reference]], so that is left as it was. */
  def makeup(amount: Double): Double = Reference - curve(Reference, amount)

  /** A square of a level in dBFS; [[Floor]] for silence. */
  def decibels(square: Double): Double = if (square <= 1e-12) Floor else 10.0 * math.log10(square)
}
