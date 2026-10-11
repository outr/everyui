package everyui.audio

/** Voices brought forward in any sound, stereo as much as surround: an equaliser that lifts the band speech is
  * understood by, takes a little off the low middle that muddies it, and cuts the rumble under it. What
  * MediaSupport.voiceClarity sets, at a strength from 0 (nothing: every sample passes exactly as it came) to 1.
  *
  * At full strength it is three of RBJ's filters ([[Biquad]]) in a row, on every channel but the LFE (which carries
  * no speech, and whose rumble is the point of it):
  *  - a high-pass at [[VoiceClarity.HighPass]] Hz (Butterworth, 12 dB an octave): the rumble, wind and traffic that
  *    share the voices' loudness without carrying any of their words;
  *  - a dip of [[VoiceClarity.Dip]] dB at [[VoiceClarity.DipHz]] Hz: the boom of a room and a chest, which masks the
  *    consonants above it;
  *  - a broad lift of [[VoiceClarity.Presence]] dB at [[VoiceClarity.PresenceHz]] Hz, about an octave and a third
  *    wide: where the consonants are, and the ear most sensitive.
  * A strength between is each of them in proportion: the lift and the dip in dB, the high-pass's frequency (so at a
  * strength near 0 it is far under anything heard). A change of strength glides over [[Downmix.GlideSeconds]] rather
  * than clicking, the filters designed afresh every [[VoiceClarity.Step]] frames as it goes.
  *
  * It lifts, so what comes out can be louder than what went in: [[ListeningAids]] runs it ahead of a limiter.
  * Interleaved samples, `channels` to a frame, in the order [[Downmix.roles]] gives them. Not thread safe. Written as
  * loops over indices, for the Roku's compiler. */
final class VoiceClarity(val channels: Int, val rate: Int) {
  private val high = new Biquad(channels)
  private val dip = new Biquad(channels)
  private val presence = new Biquad(channels)
  // the LFE is passed by
  private val skip: Array[Boolean] = Array.tabulate(channels)(c => Downmix.roles(channels).indexOf('E') == c)
  private var wanted: Double = 0.0
  private var now: Double = 0.0
  private var designed: Double = -1.0
  private var since: Int = 0
  private val glide: Double = 1.0 - math.exp(-1.0 / (rate * Downmix.GlideSeconds))

  /** How strong, 0 to 1 ([[VoiceClarity.clamp]]): heard from the next frame, gliding there. */
  def level(strength: Double): Unit = wanted = VoiceClarity.clamp(strength)

  /** The strength now, after its glide. */
  def strength: Double = now

  /** Whether it is doing nothing: at 0, and not gliding anywhere. */
  def quiet: Boolean = now == 0.0 && wanted == 0.0

  /** What the three filters together do to a sine at `hz` at the strength now, in dB. */
  def response(hz: Double): Double = {
    design()
    if (now == 0.0) 0.0 else high.response(rate, hz) + dip.response(rate, hz) + presence.response(rate, hz)
  }

  /** `count` frames of `samples` (interleaved), each through the filters in place; at 0, left exactly as they are. */
  def process(samples: Array[Float], count: Int): Unit = if (!quiet) {
    var f = 0
    while (f < count) {
      if (now != wanted) {
        now += (wanted - now) * glide
        if (math.abs(wanted - now) < 1e-6) now = wanted
      }
      if (now == 0.0) {
        // glided back to nothing: from here on passed by, and the filters' memory let go
        high.clear()
        dip.clear()
        presence.clear()
        designed = -1.0
      }
      else {
        since -= 1
        if (designed != now && since <= 0) design()
        val at = f * channels
        var c = 0
        while (c < channels) {
          if (!skip(c)) {
            val x = samples(at + c).toDouble
            samples(at + c) = presence.run(dip.run(high.run(x, c), c), c).toFloat
          }
          c += 1
        }
      }
      f += 1
    }
  }

  // the filters for the strength now
  private def design(): Unit = if (designed != now) {
    designed = now
    since = VoiceClarity.Step
    high.highPass(rate, VoiceClarity.HighPass * now, VoiceClarity.ButterworthQ)
    dip.peaking(rate, VoiceClarity.DipHz, VoiceClarity.DipQ, VoiceClarity.Dip * now)
    presence.peaking(rate, VoiceClarity.PresenceHz, VoiceClarity.PresenceQ, VoiceClarity.Presence * now)
  }
}

object VoiceClarity {
  /** The high-pass's frequency at full strength, in Hz: under a man's voice's lowest (about 100), over the rumble. */
  val HighPass: Double = 90.0
  val ButterworthQ: Double = 1.0 / math.sqrt(2.0)
  /** The dip at full strength, in dB, about [[DipHz]]. */
  val Dip: Double = -3.0
  val DipHz: Double = 300.0
  val DipQ: Double = 1.0
  /** The lift at full strength, in dB, about [[PresenceHz]]. */
  val Presence: Double = 6.0
  val PresenceHz: Double = 2800.0
  val PresenceQ: Double = 1.0
  /** How many frames a glide keeps a design before the next. */
  val Step: Int = 16

  /** `strength` within 0 and 1; anything not a number is 0. */
  def clamp(strength: Double): Double = if (strength.isNaN) 0.0 else math.max(0.0, math.min(1.0, strength))
}
