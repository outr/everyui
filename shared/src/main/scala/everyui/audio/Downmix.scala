package everyui.audio

/** Sound of more than two channels folded down to the two a stereo output plays (a television's own speakers), with
  * its centre, where a film keeps its dialogue, at a level of the viewer's choosing: what Kodi calls the downmix's
  * centre mix level. Folded the usual way, a film's voices sit under its music and effects; raised a few decibels,
  * they come back over them.
  *
  * The fold is ITU-R BS.775's: each side takes its own front channel whole, the centre at -3 dB ([[Downmix.Center]],
  * raised or lowered by [[center]]) and its own surrounds at -3 dB ([[Downmix.Surround]]); a back centre (6.1) goes
  * to both at -6 dB, and the LFE is left out, as BS.775 and a Dolby decoder's Lo/Ro leave it. At 0 dB it is the fold
  * a browser makes of a 5.1 stream. What comes out is kept under full scale by a limiter that the two
  * channels share, so the picture of the sound does not lean: its gain falls at once to what a peak needs to stay
  * under [[Downmix.Ceiling]], and comes back over [[Downmix.ReleaseSeconds]]; a change of centre glides over
  * [[Downmix.GlideSeconds]] rather than clicking.
  *
  * Where the output plays every channel itself (an AV receiver taking 5.1 or 7.1 as PCM), nothing is folded: [[lift]]
  * raises or lowers the centre in place by the same decibels (10^(dB/20), [[Downmix.liftGain]]), with the same glide,
  * and leaves every other channel exactly as it came. Its limiter is the centre's alone, so a raised centre never
  * ducks the music and effects around it: it keeps the centre under [[Downmix.Ceiling]], or under what the centre came
  * in at where that was louder still, so it never takes the centre below where it was.
  *
  * The channels are in the order Android, a WAV file and Media3 give them ([[Downmix.roles]]). Two channels or one,
  * or more without a centre (a quad), are none of its business: [[Downmix.applies]] says so, and they are not given
  * to it. Not thread safe: one is fed from the one thread that plays it. Written as loops over indices, for the
  * Roku's compiler.
  *
  * {{{
  *   val fold = new Downmix(6, 48000)
  *   fold.center(6.0)
  *   fold.mix(frames, count, stereo)   // interleaved in, two to a frame out
  *   fold.lift(frames, count, frames)  // or the centre raised in place, every channel out
  * }}} */
final class Downmix(val channels: Int, val rate: Int) {
  /** What each channel is. */
  val roles: String = Downmix.roles(channels)
  private val centre: Int = roles.indexOf('C')
  // each channel's share of the left and the right, the centre's apart (it moves)
  private val lefts: Array[Double] = Array.tabulate(channels)(i => Downmix.left(roles.charAt(i)))
  private val rights: Array[Double] = Array.tabulate(channels)(i => Downmix.right(roles.charAt(i)))
  private var wanted: Double = Downmix.Center
  private var shared: Double = Downmix.Center
  private var gain: Double = 1.0
  private val glide: Double = 1.0 - math.exp(-1.0 / (rate * Downmix.GlideSeconds))
  private val release: Double = 1.0 - math.exp(-1.0 / (rate * Downmix.ReleaseSeconds))
  // what has gone through since it was last read (heard)
  private var frames: Long = 0L
  private var centreSum: Double = 0.0
  private var leftSum: Double = 0.0
  private var rightSum: Double = 0.0
  private var least: Double = 1.0
  // whether what went through was lifted rather than folded, for what heard says
  private var lifted: Boolean = false

  /** The centre at `db` decibels over its usual -3 dB (0 is the usual fold), within [[Downmix.Lowest]] and
    * [[Downmix.Highest]]. Heard from the next frame, gliding there. */
  def center(db: Double): Unit = wanted = Downmix.centerGain(db)

  /** The centre's share of each side now, after its glide. */
  def centerShare: Double = shared

  /** The limiter's gain now: 1 where it is doing nothing. */
  def limiting: Double = gain

  /** `count` frames of `in` (interleaved, [[channels]] samples to a frame, -1 to 1), folded into `out` as two to a
    * frame. No sample out is past [[Downmix.Ceiling]]. */
  def mix(in: Array[Float], count: Int, out: Array[Float]): Unit = {
    var f = 0
    while (f < count) {
      val at = f * channels
      var l = 0.0
      var r = 0.0
      var i = 0
      while (i < channels) {
        val x = in(at + i).toDouble
        l += lefts(i) * x
        r += rights(i) * x
        i += 1
      }
      shared += (wanted - shared) * glide
      val c = if (centre >= 0) in(at + centre).toDouble else 0.0
      l += shared * c
      r += shared * c
      gain += (1.0 - gain) * release
      val peak = math.max(math.abs(l), math.abs(r))
      if (peak * gain > Downmix.Ceiling) gain = Downmix.Ceiling / peak
      l *= gain
      r *= gain
      out(f * 2) = l.toFloat
      out(f * 2 + 1) = r.toFloat
      centreSum += c * c
      leftSum += l * l
      rightSum += r * r
      if (gain < least) least = gain
      f += 1
    }
    frames += count
    lifted = false
  }

  /** The centre's gain in [[lift]] now, after its glide: 1 where it is as it came. */
  def centerLift: Double = shared / Downmix.Center

  /** `count` frames of `in` (interleaved, [[channels]] samples to a frame, -1 to 1) into `out` as they came, every
    * channel, but for the centre raised or lowered by the decibels [[center]] was given (10^(dB/20)) and kept by its
    * own limiter under [[Downmix.Ceiling]] (or what it came in at, where that was louder). `out` may be `in`. At 0 dB
    * every sample goes out exactly as it came in. */
  def lift(in: Array[Float], count: Int, out: Array[Float]): Unit = {
    var f = 0
    while (f < count) {
      val at = f * channels
      var i = 0
      while (i < channels) {
        out(at + i) = in(at + i)
        i += 1
      }
      shared += (wanted - shared) * glide
      // the glide's last step taken, so a centre set back to 0 dB comes out exactly as it went in
      if (math.abs(wanted - shared) < 1e-9) shared = wanted
      if (centre >= 0) {
        val c = in(at + centre).toDouble
        val raised = shared / Downmix.Center
        val peak = math.abs(c * raised)
        gain += (1.0 - gain) * release
        val ceiling = math.max(Downmix.Ceiling, math.abs(c))
        if (peak * gain > ceiling) gain = ceiling / peak
        // it takes back only what the centre was raised by, never more: at 0 dB or under, nothing
        val held = math.max(gain, math.min(1.0, 1.0 / raised))
        val y = c * raised * held
        out(at + centre) = y.toFloat
        centreSum += c * c
        leftSum += y * y
        if (held < least) least = held
      }
      f += 1
    }
    frames += count
    lifted = true
  }

  /** How loud the centre went in and each side came out since this was last asked, in dB of full scale (root mean
    * square: a full-scale sine is -3), and the most the limiter took off, in dB; then counted afresh. Lifted, it says
    * how loud the centre came out instead of each side. */
  def heard(): String = {
    val n = math.max(1L, frames).toDouble
    val out =
      if (lifted) " dBFS, out " + Downmix.decibels(math.sqrt(leftSum / n))
      else " dBFS, out left " + Downmix.decibels(math.sqrt(leftSum / n)) + " right " +
        Downmix.decibels(math.sqrt(rightSum / n))
    val said = "centre in " + Downmix.decibels(math.sqrt(centreSum / n)) + out + " dBFS, limited " +
      Downmix.decibels(least) + " dB"
    frames = 0L
    centreSum = 0.0
    leftSum = 0.0
    rightSum = 0.0
    least = 1.0
    said
  }
}

object Downmix {
  /** The centre's share of each side in the usual fold: -3 dB. */
  val Center: Double = 1.0 / math.sqrt(2.0)
  /** Each surround's share of its own side: -3 dB. */
  val Surround: Double = 1.0 / math.sqrt(2.0)
  /** The most a sample out comes to: about -0.2 dB of full scale. */
  val Ceiling: Double = 0.977
  /** How far the centre may be lowered and raised, in dB. */
  val Lowest: Double = -12.0
  val Highest: Double = 12.0
  /** How long the limiter takes to give back what it took off (to within a third). */
  val ReleaseSeconds: Double = 0.2
  /** How long a change of centre takes (to within a third). */
  val GlideSeconds: Double = 0.02

  /** What each of `channels` is, in order: L and R the front pair, C the centre, E the LFE, l and r a left and right
    * surround (side or back), b a back centre. As Android numbers a 3, 5, 6 (5.1), 7 (6.1) and 8 (7.1) channel stream;
    * "" for any other count, and for one or two, which are not folded. */
  def roles(channels: Int): String =
    if (channels == 3) "LRC"
    else if (channels == 4) "LRlr"
    else if (channels == 5) "LRClr"
    else if (channels == 6) "LRCElr"
    else if (channels == 7) "LRCElrb"
    else if (channels == 8) "LRCElrlr"
    else ""

  /** Whether a stream of `channels` is one to fold: more than two, a centre among them. */
  def applies(channels: Int): Boolean = channels > 2 && roles(channels).indexOf('C') >= 0

  /** `db` within [[Lowest]] and [[Highest]]; anything not a number is 0. */
  def clamp(db: Double): Double = if (db.isNaN) 0.0 else math.max(Lowest, math.min(Highest, db))

  /** The centre's share of each side with it raised `db` decibels: the usual -3 dB times 10^(db/20). */
  def centerGain(db: Double): Double = Center * math.pow(10.0, clamp(db) / 20.0)

  /** The centre's gain in place ([[Downmix.lift]]) with it raised `db` decibels: 10^(db/20). */
  def liftGain(db: Double): Double = math.pow(10.0, clamp(db) / 20.0)

  /** What a stream of `channels` is called: "5.1" for 6, "7.1" for 8, and so on; "" for one it does not know. */
  def layout(channels: Int): String =
    if (channels == 3) "3.0"
    else if (channels == 4) "4.0"
    else if (channels == 5) "5.0"
    else if (channels == 6) "5.1"
    else if (channels == 7) "6.1"
    else if (channels == 8) "7.1"
    else ""

  /** A channel's share of the left side (the centre's apart). */
  def left(role: Char): Double =
    if (role == 'L') 1.0 else if (role == 'l') Surround else if (role == 'b') Surround * Surround else 0.0

  /** A channel's share of the right side (the centre's apart). */
  def right(role: Char): Double =
    if (role == 'R') 1.0 else if (role == 'r') Surround else if (role == 'b') Surround * Surround else 0.0

  /** `x` as it is up to [[Knee]], and past it bent smoothly towards [[Ceiling]], never reaching it: a limiter with no
    * memory, for where a sample-by-sample one cannot run (a browser's WaveShaperNode). Its slope is 1 at the knee. */
  def softClip(x: Double): Double = {
    val a = math.abs(x)
    if (a <= Knee) x
    else {
      val room = Ceiling - Knee
      val bent = Knee + room * tanh((a - Knee) / room)
      if (x < 0.0) -bent else bent
    }
  }

  /** Where [[softClip]] starts to bend. */
  val Knee: Double = 0.8

  /** [[softClip]] as a curve of `points` over -`range` to `range`, as a WaveShaperNode takes it. */
  def softCurve(points: Int, range: Double): Array[Float] =
    Array.tabulate(points)(i => softClip(range * (2.0 * i / (points - 1) - 1.0)).toFloat)

  /** A level (0 to 1) in dB, to a tenth; -120 for silence. */
  def decibels(level: Double): String = tenths(if (level <= 1e-6) -120.0 else 20.0 * math.log10(level))

  /** A level in dB with its sign, to a tenth: "+3.0", "-6.0", "+0.0". */
  def signed(db: Double): String = (if (db >= 0.0) "+" else "") + tenths(db)

  // to a tenth, written the same on every platform (a browser writes -15.0 as "-15")
  private def tenths(value: Double): String = {
    val t = math.round(value * 10.0)
    val a = math.abs(t)
    (if (t < 0) "-" else "") + (a / 10).toString + "." + (a % 10).toString
  }

  private def tanh(x: Double): Double = if (x > 20.0) 1.0 else {
    val e = math.exp(2.0 * x)
    (e - 1.0) / (e + 1.0)
  }
}
