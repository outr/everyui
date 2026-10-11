package everyui.audio

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Voice clarity's equaliser (MediaSupport.voiceClarity), apart from any player: RBJ's filters as designed, the whole
  * of it against the analogue design it is drawn from, measured with sines, nothing touched at 0, the LFE left alone,
  * and a change of strength gliding. */
class VoiceClaritySpec extends AnyWordSpec with Matchers {
  private val rate = 48000

  private def frames(channels: Int, count: Int)(sample: (Int, Int) => Double): Array[Float] =
    Array.tabulate(channels * count)(i => sample(i % channels, i / channels).toFloat)

  private def sine(frame: Int, amplitude: Double, hz: Double): Double =
    amplitude * math.sin(2.0 * math.Pi * hz * frame / rate)

  private def rms(samples: Array[Float], channels: Int, channel: Int, from: Int): Double = {
    var sum = 0.0
    var n = 0
    var f = from
    while (f * channels + channel < samples.length) {
      sum += samples(f * channels + channel).toDouble * samples(f * channels + channel)
      n += 1
      f += 1
    }
    math.sqrt(sum / n)
  }

  private def db(level: Double): Double = 20.0 * math.log10(level)

  /** The analogue filters the design is drawn from, at `hz`, in dB: a Butterworth high-pass, and two peaks of RBJ's
    * analogue prototype, H(s) = (s² + s(A/Q) + 1) / (s² + s/(AQ) + 1) with s in units of the centre. */
  private def analogue(hz: Double, strength: Double): Double = {
    def peak(f0: Double, q: Double, gain: Double): Double = {
      val a = math.pow(10.0, gain / 40.0)
      val w = hz / f0
      val re = 1.0 - w * w
      val num = re * re + math.pow(w * a / q, 2)
      val den = re * re + math.pow(w / (a * q), 2)
      10.0 * math.log10(num / den)
    }
    val w = hz / (VoiceClarity.HighPass * strength)
    val high = 10.0 * math.log10(math.pow(w, 4) / (1.0 + math.pow(w, 4)))
    high + peak(VoiceClarity.DipHz, VoiceClarity.DipQ, VoiceClarity.Dip * strength) +
      peak(VoiceClarity.PresenceHz, VoiceClarity.PresenceQ, VoiceClarity.Presence * strength)
  }

  /** A sine at `hz` through a clarity of `strength`, settled: what it does to it, in dB. */
  private def measured(hz: Double, strength: Double, channels: Int = 2, channel: Int = 0): Double = {
    val v = new VoiceClarity(channels, rate)
    v.level(strength)
    val count = rate * 2
    val in = frames(channels, count)((_, f) => sine(f, 0.1, hz))
    val out = in.clone()
    v.process(out, count)
    // the last second: the glide and the filters long settled
    db(rms(out, channels, channel, rate) / rms(in, channels, channel, rate))
  }

  "A Biquad" should {
    "be a Butterworth high-pass: -3 dB at its frequency, -12.3 an octave under" in {
      val b = new Biquad(1)
      b.highPass(rate, 90.0, VoiceClarity.ButterworthQ)
      b.response(rate, 90.0) shouldBe -3.01 +- 0.01
      b.response(rate, 45.0) shouldBe -12.30 +- 0.02
      b.response(rate, 1000.0) shouldBe 0.0 +- 0.01
    }
    "peak by exactly its gain at its frequency, and do nothing at 0 dB" in {
      val b = new Biquad(1)
      b.peaking(rate, 2800.0, 1.0, 6.0)
      b.response(rate, 2800.0) shouldBe 6.0 +- 1e-9
      b.peaking(rate, 300.0, 1.0, -3.0)
      b.response(rate, 300.0) shouldBe -3.0 +- 1e-9
      b.peaking(rate, 2800.0, 1.0, 0.0)
      List(50.0, 300.0, 2800.0, 12000.0).foreach(hz => b.response(rate, hz) shouldBe 0.0 +- 1e-9)
    }
  }

  "Voice clarity" should {
    "lift the voices' band, dip the low middle and cut the rumble, as designed, within 0.1 dB" in {
      List(45.0, 90.0, 150.0, 300.0, 1000.0, 2000.0, 2800.0, 3500.0).foreach { hz =>
        withClue(hz.toString + " Hz: ") {
          measured(hz, 1.0) shouldBe analogue(hz, 1.0) +- 0.1
        }
      }
      // the points the design names
      measured(2800.0, 1.0) shouldBe 6.0 +- 0.1
      measured(300.0, 1.0) shouldBe -3.0 +- 0.15
      measured(45.0, 1.0) should be < -12.0
    }
    "measure as its own filters say it does" in {
      val v = new VoiceClarity(2, rate)
      v.level(1.0)
      v.process(new Array[Float](2 * rate), rate)
      List(30.0, 90.0, 300.0, 1000.0, 2800.0, 6000.0, 12000.0).foreach { hz =>
        withClue(hz.toString + " Hz: ") {
          measured(hz, 1.0) shouldBe v.response(hz) +- 0.05
        }
      }
    }
    "be each part in proportion at half strength" in {
      measured(2800.0, 0.5) shouldBe analogue(2800.0, 0.5) +- 0.1
      measured(2800.0, 0.5) shouldBe 3.0 +- 0.1
      measured(300.0, 0.5) shouldBe analogue(300.0, 0.5) +- 0.1
      // the high-pass at 45 Hz
      measured(45.0, 0.5) shouldBe analogue(45.0, 0.5) +- 0.1
    }
    "leave every sample exactly as it was at 0" in {
      List(1, 2, 6, 8).foreach { channels =>
        val v = new VoiceClarity(channels, rate)
        val in = frames(channels, 4800)((c, f) => sine(f, 0.5, 100.0 * (c + 1)))
        val out = in.clone()
        v.process(out, 4800)
        out.toList shouldBe in.toList
        v.quiet shouldBe true
      }
    }
    "pass by exactly again once it has glided back to 0" in {
      val v = new VoiceClarity(2, rate)
      v.level(1.0)
      v.process(frames(2, rate)((_, f) => sine(f, 0.3, 2800.0)), rate)
      v.level(0.0)
      v.process(frames(2, rate)((_, f) => sine(f, 0.3, 2800.0)), rate)
      v.quiet shouldBe true
      val in = frames(2, 4800)((_, f) => sine(f, 0.3, 2800.0))
      val out = in.clone()
      v.process(out, 4800)
      out.toList shouldBe in.toList
    }
    "treat every channel alike but the LFE, in mono, stereo, 5.1 and 7.1" in {
      List(1, 2, 6, 8).foreach { channels =>
        val lfe = Downmix.roles(channels).indexOf('E')
        (0 until channels).foreach { c =>
          withClue(channels.toString + " channels, channel " + c + ": ") {
            if (c == lfe) measured(2800.0, 1.0, channels, c) shouldBe 0.0 +- 1e-9
            else measured(2800.0, 1.0, channels, c) shouldBe 6.0 +- 0.1
          }
        }
      }
      // the LFE's samples, bit for bit
      val v = new VoiceClarity(6, rate)
      v.level(1.0)
      val in = frames(6, 4800)((_, f) => sine(f, 0.5, 40.0))
      val out = in.clone()
      v.process(out, 4800)
      (0 until 4800).foreach(f => out(f * 6 + 3) shouldBe in(f * 6 + 3))
    }
    "glide to a new strength rather than step to it" in {
      val v = new VoiceClarity(2, rate)
      val one = new Array[Float](2)
      v.level(1.0)
      v.process(one, 1)
      v.strength should be < 0.01
      (1 until (rate * Downmix.GlideSeconds).toInt).foreach(_ => v.process(one, 1))
      // one glide time: about two thirds of the way
      v.strength shouldBe 0.63 +- 0.02
      v.process(new Array[Float](2 * rate), rate)
      v.strength shouldBe 1.0
      // a sine at the lift's centre, sample to sample, never jumps as the strength moves
      val w = new VoiceClarity(1, rate)
      val warm = frames(1, rate)(( _, f) => sine(f, 0.5, 2800.0))
      w.process(warm, rate)
      w.level(1.0)
      val out = frames(1, rate / 10)((_, f) => sine(f + rate, 0.5, 2800.0))
      w.process(out, rate / 10)
      val steps = out.zip(out.tail).map { case (a, b) => math.abs(b - a).toDouble }
      // a sine of 0.5 at 2800 Hz moves at most 0.18 a sample; twice its amplitude (+6 dB) at most 0.37
      steps.max should be < 0.37
    }
    "take only 0 to 1, and what is not a number as 0" in {
      VoiceClarity.clamp(2.0) shouldBe 1.0
      VoiceClarity.clamp(-1.0) shouldBe 0.0
      VoiceClarity.clamp(Double.NaN) shouldBe 0.0
    }
  }
}
