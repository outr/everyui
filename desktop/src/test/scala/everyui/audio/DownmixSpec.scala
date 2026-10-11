package everyui.audio

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The fold of surround sound to stereo with its centre at a chosen level (MediaSupport.centerMix), apart from any
  * player: BS.775's shares, the centre at 0, +3 and +6 dB, the limiter under full scale on the loudest input, stereo
  * content passing as it was, 5.1, 6.1 and 7.1 laid out as Android numbers them, and the curve a browser bends with;
  * the centre lifted in place for an output that plays every channel, the rest exactly as it came; and the route
  * (fold, lift, decode here) from what the output takes, given as an Android device gives it. */
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

  "A Downmix lifting in place" should {
    /** `in` lifted, every channel out. */
    def lifted(d: Downmix, in: Array[Float]): Array[Float] = {
      val out = new Array[Float](in.length)
      d.lift(in, in.length / d.channels, out)
      out
    }
    /** One channel's root mean square from frame `from` on. */
    def level(out: Array[Float], channels: Int, channel: Int, from: Int): Double = {
      var sum = 0.0
      var n = 0
      var f = from
      while (f * channels + channel < out.length) {
        val x = out(f * channels + channel).toDouble
        sum += x * x
        n += 1
        f += 1
      }
      math.sqrt(sum / n)
    }
    val busy: (Int, Int) => Double = (c, f) => sine(f, 0.05 + 0.04 * c, 200.0 + 110.0 * c)

    "give its gain as 10^(dB/20), not the fold's share" in {
      Downmix.liftGain(0.0) shouldBe 1.0
      Downmix.liftGain(6.0) shouldBe 1.9953 +- 0.0001
      Downmix.liftGain(-6.0) shouldBe 0.5012 +- 0.0001
      Downmix.liftGain(40.0) shouldBe Downmix.liftGain(12.0)
      Downmix.layout(6) shouldBe "5.1"
      Downmix.layout(8) shouldBe "7.1"
      Downmix.layout(2) shouldBe ""
    }
    "raise and lower the centre by its decibels, in 5.1 and in 7.1" in {
      List(6, 8).foreach { channels =>
        List(3.0, 6.0, 12.0, -6.0, -12.0).foreach { db =>
          val d = new Downmix(channels, rate)
          d.center(db)
          val in = frames(channels, rate)((c, f) => if (c == 2) sine(f, 0.1) else busy(c, f))
          val out = lifted(d, in)
          // after the glide
          val gain = level(out, channels, 2, rate / 2) / level(in, channels, 2, rate / 2)
          20.0 * math.log10(gain) shouldBe db +- 0.01
        }
      }
    }
    "pass every sample exactly as it came at 0 dB, the loudest too" in {
      val d = new Downmix(8, rate)
      val random = new scala.util.Random(3)
      val in = frames(8, rate)((_, _) => random.nextDouble() * 2.0 - 1.0)
      lifted(d, in).toList shouldBe in.toList
      in(2) = 1.0f
      lifted(d, in).toList shouldBe in.toList
      // and once more after a while at +6, set back
      d.center(6.0)
      lifted(d, in)
      d.center(0.0)
      lifted(d, frames(8, rate)((c, f) => if (c == 2) sine(f, 0.1) else 0.0))
      lifted(d, in).toList shouldBe in.toList
    }
    "leave every channel but the centre untouched, at any centre" in {
      List(-12.0, 6.0, 12.0).foreach { db =>
        val d = new Downmix(6, rate)
        d.center(db)
        val in = frames(6, rate)((c, f) => if (c == 2) sine(f, 0.9) else busy(c, f))
        val out = lifted(d, in)
        (0 until rate).foreach { f =>
          (0 until 6).filter(_ != 2).foreach(c => out(f * 6 + c) shouldBe in(f * 6 + c))
        }
      }
    }
    "never take a full-scale centre past full scale at +12 dB, nor below where it came in" in {
      val d = new Downmix(6, rate)
      d.center(12.0)
      val random = new scala.util.Random(11)
      val in = frames(6, rate)((_, _) => if (random.nextBoolean()) 1.0 else -1.0)
      val out = lifted(d, in)
      val centre = (0 until rate).map(f => math.abs(out(f * 6 + 2).toDouble))
      centre.max should be <= 1.0 + 1e-6
      centre.min should be >= 1.0 - 1e-6
      // a full-scale sine raised 12 dB: kept under the ceiling, and its loudest at the ceiling
      val d2 = new Downmix(6, rate)
      d2.center(12.0)
      val out2 = lifted(d2, frames(6, rate)((c, f) => if (c == 2) sine(f, Downmix.Ceiling, 100.0) else 0.0))
      val centre2 = (0 until rate).map(f => math.abs(out2(f * 6 + 2).toDouble))
      centre2.max should be <= Downmix.Ceiling + 1e-6
      centre2.max should be >= Downmix.Ceiling - 0.01
      d2.limiting should be < 0.3
      // a quiet one under it at +6 is raised the whole 6 dB, the limiter given back
      val out3 = lifted(d2, frames(6, rate)((c, f) => if (c == 2) sine(f, 0.1) else 0.0))
      d2.limiting should be > 0.99
      level(out3, 6, 2, rate * 9 / 10) shouldBe (0.1 * s * Downmix.liftGain(12.0)) +- 0.003
    }
    "glide to a new centre rather than step to it" in {
      val d = new Downmix(6, rate)
      val steady = frames(6, 1)((c, _) => if (c == 2) 0.5 else 0.0)
      lifted(d, steady)
      d.center(6.0)
      lifted(d, steady)
      // one frame moves it a little of the way
      (d.centerLift - 1.0) should be < 0.01
      (1 until (rate * Downmix.GlideSeconds).toInt).foreach(_ => lifted(d, steady))
      // one glide time: about two thirds of the way
      ((d.centerLift - 1.0) / (Downmix.liftGain(6.0) - 1.0)) shouldBe 0.63 +- 0.02
      lifted(d, frames(6, rate)((c, _) => if (c == 2) 0.2 else 0.0))
      d.centerLift shouldBe Downmix.liftGain(6.0) +- 1e-6
    }
    "say how loud the centre went in and came out" in {
      val d = new Downmix(6, rate)
      d.center(6.0)
      lifted(d, frames(6, rate)((c, f) => if (c == 2) sine(f, 0.25) else 0.0))
      d.heard()
      lifted(d, frames(6, rate)((c, f) => if (c == 2) sine(f, 0.25) else 0.0))
      d.heard() shouldBe "centre in -15.1 dBFS, out -9.1 dBFS, limited 0.0 dB"
    }
  }

  "A CenterRoute" should {
    "fold for an output of two channels and lift for one of more, or one that does not say" in {
      CenterRoute.of(6, 2) shouldBe CenterRoute.Fold
      CenterRoute.of(8, 2) shouldBe CenterRoute.Fold
      CenterRoute.of(6, 1) shouldBe CenterRoute.Fold
      CenterRoute.of(6, 6) shouldBe CenterRoute.Lift
      CenterRoute.of(8, 8) shouldBe CenterRoute.Lift
      CenterRoute.of(6, 8) shouldBe CenterRoute.Lift
      CenterRoute.of(6, 0) shouldBe CenterRoute.Lift
      // nothing to do for stereo, mono and quad, whatever the output
      CenterRoute.of(2, 8) shouldBe CenterRoute.Leave
      CenterRoute.of(1, 2) shouldBe CenterRoute.Leave
      CenterRoute.of(4, 2) shouldBe CenterRoute.Leave
    }
    "read an output's PCM channels from its profiles, a bitstream's not counted" in {
      val stereo = Seq(0x3, 0xc)
      val surround = Seq(0x3, 0xfc, 0x63f)   // stereo, 5.1 and 7.1 as Android writes them
      // a television: stereo PCM, and AC-3 and E-AC-3 to decode itself
      CenterRoute.pcmChannels(Seq(2 -> Seq(0xc), 5 -> Seq(0xfc), 6 -> Seq(0xfc)), Nil) shouldBe 2
      // a receiver taking 7.1 as PCM, float and 16-bit
      CenterRoute.pcmChannels(Seq(2 -> stereo, 4 -> surround, 7 -> Seq(0x63f)), Nil) shouldBe 8
      // one that takes 5.1 only as a bitstream (an optical or ARC link)
      CenterRoute.pcmChannels(Seq(2 -> Seq(0xc), 5 -> Seq(0xfc)), Seq(6)) shouldBe 2
      // no profiles (before Android 12): its channel counts, else nothing known
      CenterRoute.pcmChannels(Nil, Seq(2, 6, 8)) shouldBe 8
      CenterRoute.pcmChannels(Nil, Nil) shouldBe 0
      CenterRoute.of(6, CenterRoute.pcmChannels(Seq(2 -> stereo, 4 -> surround), Nil)) shouldBe CenterRoute.Lift
      CenterRoute.of(6, CenterRoute.pcmChannels(Seq(2 -> Seq(0xc), 5 -> Seq(0xfc)), Nil)) shouldBe CenterRoute.Fold
    }
    "decode here what the output takes encoded only while the centre is not at 0, and only with a decoder" in {
      // at 0 a receiver gets its bitstream as ever
      CenterRoute.decodeHere(0.0, passes = true, decodable = true) shouldBe false
      CenterRoute.decodeHere(Double.NaN, passes = true, decodable = true) shouldBe false
      CenterRoute.decodeHere(3.0, passes = true, decodable = true) shouldBe true
      CenterRoute.decodeHere(-6.0, passes = true, decodable = true) shouldBe true
      // no decoder here (TrueHD, DTS on most devices): passed through, and the centre out of reach
      CenterRoute.decodeHere(6.0, passes = true, decodable = false) shouldBe false
      CenterRoute.supported(encoded = true, decodable = false) shouldBe false
      // what the output cannot take encoded is decoded here anyway
      CenterRoute.decodeHere(6.0, passes = false, decodable = true) shouldBe false
      CenterRoute.supported(encoded = true, decodable = true) shouldBe true
      CenterRoute.supported(encoded = false, decodable = false) shouldBe true
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
