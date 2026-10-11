package everyui.audio

/** The last word on how loud any sample may be: [[Downmix]]'s limiter, for any number of channels. One gain for them
  * all, so the picture of the sound does not lean: it falls at once to what the loudest sample of a frame needs to
  * stay under [[Downmix.Ceiling]], and comes back over [[Downmix.ReleaseSeconds]]. Interleaved samples, `channels`
  * to a frame. Not thread safe. Written as loops over indices, for the Roku's compiler. */
final class Limiter(val channels: Int, val rate: Int) {
  private var gain: Double = 1.0
  private val release: Double = 1.0 - math.exp(-1.0 / (rate * Downmix.ReleaseSeconds))
  private var least: Double = 1.0

  /** Its gain now: 1 where it is doing nothing. */
  def limiting: Double = gain

  /** The least gain since this was last asked; then counted afresh. */
  def most(): Double = {
    val l = least
    least = gain
    l
  }

  /** `count` frames of `samples` (interleaved), brought under the ceiling in place. */
  def process(samples: Array[Float], count: Int): Unit = {
    var f = 0
    while (f < count) {
      if (gain < 1.0) {
        gain += (1.0 - gain) * release
        // given all back: from here on it multiplies by exactly 1
        if (gain > 0.99999) gain = 1.0
      }
      val at = f * channels
      var peak = 0.0
      var c = 0
      while (c < channels) {
        val a = math.abs(samples(at + c).toDouble)
        if (a > peak) peak = a
        c += 1
      }
      if (peak * gain > Downmix.Ceiling) gain = Downmix.Ceiling / peak
      if (gain < 1.0) {
        c = 0
        while (c < channels) {
          samples(at + c) = (samples(at + c) * gain).toFloat
          c += 1
        }
        if (gain < least) least = gain
      }
      f += 1
    }
  }
}
