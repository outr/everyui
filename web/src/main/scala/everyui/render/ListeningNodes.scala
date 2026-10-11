package everyui.render

import everyui.audio.{Downmix, NightMode, VoiceClarity}

import scala.scalajs.js

/** The listening aids (MediaSupport.voiceClarity and nightMode, [[everyui.audio.ListeningAids]]) as Web Audio nodes
  * on the page's context, in the order the shared ones run: voice clarity's three filters (BiquadFilterNodes, which
  * are RBJ's, as [[everyui.audio.Biquad]] is), then night mode (a DynamicsCompressorNode) and a gain that sets its
  * makeup to the shared one's. [[CenterFold]] puts them between its fold and its limiter, so the limiter is the last
  * word on both.
  *
  * The browser's compressor is not the shared one, and is set to come as close to it as it can: it hears a peak
  * where [[NightMode]] hears a root mean square, and its knee starts at its threshold where the shared one is centred
  * on it, so its threshold is 3 dB higher (a sine's peak is 3 dB over its root mean square) and half a knee lower,
  * and a steady tone settles about where the shared curve puts it; its knee is shaped otherwise; it looks 6 ms
  * ahead (the sound comes out that much later) and its release quickens with the reduction; and it makes up gain of
  * its own, 0.6 of what it takes off a full-scale peak in dB, which [[trim]] takes back and replaces with the shared
  * makeup. Every change glides over [[Downmix.GlideSeconds]]. At 0 each does nothing: the high-pass at 0 Hz passes
  * everything, the peaks are at 0 dB and the ratio 1 with no knee. */
final class ListeningNodes(ctx: js.Dynamic) {
  private val high = filter("highpass", 0.0, ListeningNodes.HighQ)
  private val dip = filter("peaking", VoiceClarity.DipHz, VoiceClarity.DipQ)
  private val presence = filter("peaking", VoiceClarity.PresenceHz, VoiceClarity.PresenceQ)
  private val squeeze: js.Dynamic = ctx.createDynamicsCompressor()
  private val makeup: js.Dynamic = ctx.createGain()
  squeeze.knee.value = 0.0
  squeeze.attack.value = NightMode.AttackSeconds
  squeeze.release.value = NightMode.ReleaseSeconds
  squeeze.ratio.value = 1.0
  squeeze.threshold.value = 0.0
  high.connect(dip)
  dip.connect(presence)
  presence.connect(squeeze)
  squeeze.connect(makeup)

  /** Where the sound goes in, and where it comes out. */
  val input: js.Dynamic = high
  val output: js.Dynamic = makeup

  /** Voice clarity at `clarity` and night mode at `night`, each 0 to 1, gliding there. */
  def set(clarity: Double, night: Double): Unit = {
    val at = ctx.currentTime
    val glide = Downmix.GlideSeconds
    high.frequency.setTargetAtTime(VoiceClarity.HighPass * clarity, at, glide)
    dip.gain.setTargetAtTime(VoiceClarity.Dip * clarity, at, glide)
    presence.gain.setTargetAtTime(VoiceClarity.Presence * clarity, at, glide)
    squeeze.threshold.setTargetAtTime(ListeningNodes.threshold(night), at, glide)
    squeeze.knee.setTargetAtTime(if (night == 0.0) 0.0 else NightMode.Knee, at, glide)
    squeeze.ratio.setTargetAtTime(NightMode.ratio(night), at, glide)
    makeup.gain.setTargetAtTime(math.pow(10.0, ListeningNodes.trim(night) / 20.0), at, glide)
  }

  /** What the compressor is taking off now, in dB (0 or less), as the browser says. */
  def reduction: Double = squeeze.reduction.asInstanceOf[Double]

  private def filter(kind: String, hz: Double, q: Double): js.Dynamic = {
    val f = ctx.createBiquadFilter()
    f.`type` = kind
    f.frequency.value = hz
    f.Q.value = q
    f.gain.value = 0.0
    f
  }
}

object ListeningNodes {
  /** A Web Audio high-pass's Q is in dB: Butterworth's 1/sqrt(2) is -3.01. */
  val HighQ: Double = 20.0 * math.log10(VoiceClarity.ButterworthQ)

  /** The browser compressor's threshold at `amount`: the shared one as a peak (3 dB over a sine's root mean square),
    * less half the knee, which the browser starts at its threshold; 0 dBFS at 0, where the ratio of 1 does nothing. */
  def threshold(amount: Double): Double =
    if (amount == 0.0) 0.0 else NightMode.threshold(amount) + 3.0 - NightMode.Knee / 2.0

  /** The gain after the browser's compressor at `amount`, in dB: the shared makeup, less the browser's own (0.6 of
    * what its curve takes off a full-scale peak, its knee's middle where a hard knee would be). */
  def trim(amount: Double): Double = {
    val t = if (amount == 0.0) 0.0 else threshold(amount) + NightMode.Knee / 2.0
    val r = NightMode.ratio(amount)
    val own = 0.6 * (-t) * (1.0 - 1.0 / r)
    NightMode.makeup(amount) - own
  }
}
