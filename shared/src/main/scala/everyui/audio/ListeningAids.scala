package everyui.audio

/** The two listening aids a player's sound goes through after its centre is mixed ([[Downmix]]), in this order:
  * [[VoiceClarity]] (the voices' band lifted, the rumble cut), then [[NightMode]] (the loud brought down, the quiet
  * up), then a [[Limiter]] that keeps every sample under [[Downmix.Ceiling]], since both can make a peak louder.
  * MediaSupport.voiceClarity and nightMode set them, each 0 to 1.
  *
  * With both at 0, and nothing left to glide or give back, it does nothing at all: every sample passes as it came,
  * bit for bit ([[quiet]]). Interleaved samples, `channels` to a frame, any number of them (mono, stereo, 5.1 and 7.1
  * in [[Downmix.roles]]'s order: the equaliser leaves the LFE alone, the compressor and the limiter hear and move
  * every channel together). Not thread safe: one is fed from the one thread that plays it. Written as loops over
  * indices, for the Roku's compiler.
  *
  * {{{
  *   val aids = new ListeningAids(2, 48000)
  *   aids.voiceClarity(0.5)
  *   aids.nightMode(1.0)
  *   aids.process(frames, count)   // in place
  * }}} */
final class ListeningAids(val channels: Int, val rate: Int) {
  val clarity: VoiceClarity = new VoiceClarity(channels, rate)
  val night: NightMode = new NightMode(channels, rate)
  val ceiling: Limiter = new Limiter(channels, rate)
  // what has gone through since it was last read (heard)
  private var frames: Long = 0L
  private var inSum: Double = 0.0
  private var outSum: Double = 0.0

  /** Voice clarity's strength, 0 to 1. */
  def voiceClarity(strength: Double): Unit = clarity.level(strength)

  /** Night mode's amount, 0 to 1. */
  def nightMode(amount: Double): Unit = night.level(amount)

  /** Whether it is doing nothing: both at 0, and the limiter's gain given back. */
  def quiet: Boolean = clarity.quiet && night.quiet && ceiling.limiting == 1.0

  /** `count` frames of `samples` (interleaved), through both aids and the limiter in place; left exactly as they are
    * while it is [[quiet]]. */
  def process(samples: Array[Float], count: Int): Unit = if (!quiet) {
    val n = count * channels
    inSum += sum(samples, n)
    clarity.process(samples, count)
    night.process(samples, count)
    ceiling.process(samples, count)
    outSum += sum(samples, n)
    frames += count
  }

  /** How loud it went in and came out since this was last asked (the root mean square of every channel, in dBFS: a
    * full-scale sine is -3), the least and most night mode's gain was, and the most the limiter took off, in dB; then
    * counted afresh. */
  def heard(): String = {
    val n = math.max(1L, frames * channels).toDouble
    val (low, high) = night.range()
    val said = "in " + Downmix.decibels(math.sqrt(inSum / n)) + " dBFS, out " + Downmix.decibels(math.sqrt(outSum / n)) +
      " dBFS, night mode " + Downmix.signed(low) + " to " + Downmix.signed(high) + " dB, limited " +
      Downmix.decibels(ceiling.most()) + " dB"
    frames = 0L
    inSum = 0.0
    outSum = 0.0
    said
  }

  private def sum(samples: Array[Float], n: Int): Double = {
    var s = 0.0
    var i = 0
    while (i < n) {
      s += samples(i).toDouble * samples(i)
      i += 1
    }
    s
  }
}

object ListeningAids {
  /** An aid's level as it is logged, to a hundredth, written the same on every platform: "0.50", "1.00". */
  def said(level: Double): String = {
    val h = math.round(level * 100.0)
    (h / 100).toString + "." + (if (h % 100 < 10) "0" else "") + (h % 100).toString
  }
}
