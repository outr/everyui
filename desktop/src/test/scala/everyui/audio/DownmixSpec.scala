package everyui.audio

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The fold of surround sound to stereo with its centre at a chosen level (MediaSupport.centerMix), apart from any
  * player: BS.775's shares, the centre at 0, +3 and +6 dB, the limiter under full scale on the loudest input, stereo
  * content passing as it was, 5.1, 6.1 and 7.1 laid out as Android numbers them, and the curve a browser bends with. */
class DownmixSpec extends AnyWordSpec with Matchers {
  private val rate = 48000
  private val s = 1.0 / math.sqrt(2.0)

  /** `frames` frames of `channels`, each channel's sample from `sample(channel, frame)`. */
  private def frames(channels: Int, count: Int)(sample: (Int, Int) => Double): Array[Float] =
    Array.tabulate(channels * count)(i => sample(i % channels, i / channels).toFloat)

  private def sine(frame: Int, amplitude: Double, hz: Double = 1000.0): Double =
    amplitude * math.sin(2.0 * math.Pi * hz * frame / rate)

  /** `in` folded, a frame at a time or all at once. */
  private def folded(d: Downmix, in: Array[Float]): Array[Float] = {
    val count = in.length / d.channels
    val out = new Array[Float](count * 2)
    d.mix(in, count, out)
    out
  }

  private def rms(out: Array[Float], side: Int, from: Int = 0): Double = {
    var sum = 0.0
    var n = 0
    var f = from
    while (f * 2 + side < out.length) {
      sum += out(f * 2 + side) * out(f * 2 + side)
      n += 1
      f += 1
    }
    math.sqrt(sum / n)
  }

  "Downmix's shares" should {
    "be BS.775's: the centre and the surrounds at -3 dB" in {
      Downmix.Center shouldBe s +- 1e-12
      Downmix.Surround shouldBe s +- 1e-12
      20.0 * math.log10(Downmix.Center) shouldBe -3.01 +- 0.01
    }
    "raise the centre by its decibels" in {
      Downmix.centerGain(0.0) shouldBe s +- 1e-12
      Downmix.centerGain(3.0) shouldBe (s * math.pow(10.0, 3.0 / 20.0)) +- 1e-12
      Downmix.centerGain(3.0) shouldBe 0.9988 +- 0.0001
      Downmix.centerGain(6.0) shouldBe 1.4109 +- 0.0001
      Downmix.centerGain(-6.0) shouldBe 0.3544 +- 0.0001
    }
    "keep the level within -12 and +12 dB, and take what is not a number as 0" in {
      Downmix.clamp(20.0) shouldBe 12.0
      Downmix.clamp(-40.0) shouldBe -12.0
      Downmix.clamp(Double.NaN) shouldBe 0.0
      Downmix.centerGain(100.0) shouldBe Downmix.centerGain(12.0)
    }
  }

  "Downmix's layouts" should {
    "fold only more than two channels with a centre among them" in {
      List(1, 2, 3, 4, 5, 6, 7, 8, 9).map(Downmix.applies) shouldBe
        List(false, false, true, false, true, true, true, true, false)
    }
    "name the channels as Android orders them" in {
      Downmix.roles(6) shouldBe "LRCElr"
      Downmix.roles(8) shouldBe "LRCElrlr"
      Downmix.roles(7) shouldBe "LRCElrb"
      Downmix.roles(5) shouldBe "LRClr"
      Downmix.roles(3) shouldBe "LRC"
      Downmix.roles(2) shouldBe ""
    }
  }

  "A Downmix" should {
    "fold 5.1 as BS.775 does, the LFE left out" in {
      val d = new Downmix(6, rate)
      val in = Array[Float](0.1f, 0.2f, 0.3f, 0.9f, 0.05f, 0.07f)
      val out = folded(d, in)
      out(0).toDouble shouldBe (0.1 + s * 0.3 + s * 0.05) +- 1e-6
      out(1).toDouble shouldBe (0.2 + s * 0.3 + s * 0.07) +- 1e-6
      // the LFE alone comes to nothing
      folded(d, Array[Float](0f, 0f, 0f, 1f, 0f, 0f)).toList shouldBe List(0f, 0f)
    }
    "fold 7.1's side and back surrounds each to its own side, and 6.1's back centre to both at -6 dB" in {
      val seven = folded(new Downmix(8, rate), Array[Float](0f, 0f, 0f, 0f, 0.1f, 0.2f, 0.3f, 0.4f))
      seven(0).toDouble shouldBe (s * 0.1 + s * 0.3) +- 1e-6
      seven(1).toDouble shouldBe (s * 0.2 + s * 0.4) +- 1e-6
      val six = folded(new Downmix(7, rate), Array[Float](0f, 0f, 0f, 0f, 0f, 0f, 0.4f))
      six(0).toDouble shouldBe 0.2 +- 1e-6
      six(1).toDouble shouldBe 0.2 +- 1e-6
    }
    "raise the centre 3 and 6 dB" in {
      def level(db: Double): Double = {
        val d = new Downmix(6, rate)
        d.center(db)
        val out = folded(d, frames(6, rate)((c, f) => if (c == 2) sine(f, 0.2) else 0.0))
        // after the glide
        rms(out, 0, rate / 2)
      }
      val usual = level(0.0)
      usual shouldBe (0.2 * s * s) +- 0.001
      20.0 * math.log10(level(3.0) / usual) shouldBe 3.0 +- 0.01
      20.0 * math.log10(level(6.0) / usual) shouldBe 6.0 +- 0.01
      20.0 * math.log10(level(-6.0) / usual) shouldBe -6.0 +- 0.01
    }
    "say how loud the centre went in and each side came out" in {
      val d = new Downmix(6, rate)
      folded(d, frames(6, rate)((c, f) => if (c == 2) sine(f, 1.0) else 0.0))
      d.heard() shouldBe "centre in -3.0 dBFS, out left -6.0 right -6.0 dBFS, limited 0.0 dB"
      // counted afresh
      d.heard() should startWith("centre in -120.0 dBFS")
    }
    "leave stereo content in a 5.1 stream as it was, at any centre" in {
      List(0.0, 6.0, 12.0).foreach { db =>
        val d = new Downmix(6, rate)
        d.center(db)
        val in = frames(6, 4800)((c, f) => if (c == 0) sine(f, 0.5, 440.0) else if (c == 1) sine(f, 0.3, 660.0) else 0.0)
        val out = folded(d, in)
        (0 until 4800).foreach { f =>
          out(f * 2) shouldBe in(f * 6)
          out(f * 2 + 1) shouldBe in(f * 6 + 1)
        }
      }
    }
    "never go past full scale, however loud every channel and however high the centre" in {
      val d = new Downmix(8, rate)
      d.center(12.0)
      val random = new scala.util.Random(7)
      val loud = frames(8, rate)((_, _) => if (random.nextBoolean()) 1.0 else -1.0)
      val out = folded(d, loud)
      out.map(x => math.abs(x.toDouble)).max should be <= Downmix.Ceiling + 1e-6
      d.limiting should be < 0.3
      // full-scale sines in every channel, in step: the sum is over five times full scale
      val d2 = new Downmix(6, rate)
      d2.center(12.0)
      val out2 = folded(d2, frames(6, rate)((_, f) => sine(f, 1.0, 100.0)))
      out2.map(x => math.abs(x.toDouble)).max should be <= Downmix.Ceiling + 1e-6
      out2.map(x => math.abs(x.toDouble)).max should be >= Downmix.Ceiling - 0.01
    }
    "give back what it took off once the loud part is over" in {
      val d = new Downmix(6, rate)
      d.center(6.0)
      folded(d, frames(6, rate / 10)((_, _) => 1.0))
      d.limiting should be < 0.5
      // a second of quiet: five of its release times
      val out = folded(d, frames(6, rate)((c, f) => if (c == 2) sine(f, 0.1) else 0.0))
      d.limiting should be > 0.99
      rms(out, 0, rate * 9 / 10) shouldBe (0.1 * s * Downmix.centerGain(6.0)) +- 0.002
    }
    "glide to a new centre rather than step to it" in {
      val d = new Downmix(6, rate)
      val steady = frames(6, 1)((c, _) => if (c == 2) 0.5 else 0.0)
      folded(d, steady)
      d.center(6.0)
      val before = d.centerShare
      folded(d, steady)
      // one frame moves it a little of the way
      (d.centerShare - before) should be < 0.01
      (1 until (rate * Downmix.GlideSeconds).toInt).foreach(_ => folded(d, steady))
      // one glide time: about two thirds of the way
      ((d.centerShare - s) / (Downmix.centerGain(6.0) - s)) shouldBe 0.63 +- 0.02
      folded(d, frames(6, rate)((c, _) => if (c == 2) 0.5 else 0.0))
      d.centerShare shouldBe Downmix.centerGain(6.0) +- 1e-6
    }
  }

  "Downmix.softClip" should {
    "leave what is under its knee as it is" in {
      List(0.0, 0.3, -0.5, 0.8, -0.8).foreach(x => Downmix.softClip(x) shouldBe x)
    }
    "bend smoothly past it, towards the ceiling and never to it" in {
      val xs = (0 to 4000).map(i => i / 1000.0)
      val ys = xs.map(Downmix.softClip)
      ys.zip(ys.tail).foreach { case (a, b) => b should be >= a }
      ys.max should be <= Downmix.Ceiling
      Downmix.softClip(4.0) shouldBe Downmix.Ceiling +- 0.001
      Downmix.softClip(-4.0) shouldBe -Downmix.softClip(4.0)
      // its slope is 1 either side of the knee
      ((Downmix.softClip(0.8 + 1e-6) - Downmix.softClip(0.8)) / 1e-6) shouldBe 1.0 +- 0.001
    }
    "make the curve a WaveShaperNode takes" in {
      val curve = Downmix.softCurve(8193, 4.0)
      curve.length shouldBe 8193
      curve(4096) shouldBe 0.0f
      curve(0).toDouble shouldBe -Downmix.softClip(4.0) +- 1e-6
      curve(8192).toDouble shouldBe Downmix.softClip(4.0) +- 1e-6
      // a quarter of the way up is -2, past the knee: bent
      curve(2048).toDouble shouldBe Downmix.softClip(-2.0) +- 1e-6
    }
  }
}
