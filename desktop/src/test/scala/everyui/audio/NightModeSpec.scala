package everyui.audio

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Night mode's compressor (MediaSupport.nightMode), apart from any player: its static curve and makeup, the levels it
  * settles to, its attack and release, every channel moved together, nothing touched at 0, and the glide. */
class NightModeSpec extends AnyWordSpec with Matchers {
  private val rate = 48000

  private def frames(channels: Int, count: Int)(sample: (Int, Int) => Double): Array[Float] =
    Array.tabulate(channels * count)(i => sample(i % channels, i / channels).toFloat)

  // a square wave: the same level at every sample, so its root mean square is its amplitude
  private def square(frame: Int, dbfs: Double): Double =
    math.pow(10.0, dbfs / 20.0) * (if ((frame / 24) % 2 == 0) 1.0 else -1.0)

  private def run(n: NightMode, samples: Array[Float]): Array[Float] = {
    n.process(samples, samples.length / n.channels)
    samples
  }

  /** The gain in dB at every frame of `count` at `dbfs`, one frame at a time. */
  private def gains(n: NightMode, dbfs: Double, count: Int, start: Int = 0): Array[Double] =
    Array.tabulate(count) { f =>
      run(n, frames(n.channels, 1)((_, _) => square(start + f, dbfs)))
      n.gainDb
    }

  "Night mode's curve" should {
    "be set by its amount: the threshold -20 to -30 dBFS, the ratio 1 to 4" in {
      NightMode.threshold(0.0) shouldBe -20.0
      NightMode.threshold(1.0) shouldBe -30.0
      NightMode.ratio(0.0) shouldBe 1.0
      NightMode.ratio(1.0) shouldBe 4.0
      NightMode.ratio(0.5) shouldBe 2.5
    }
    "leave what is under the knee, and take a ratio's share of what is over it" in {
      NightMode.curve(-50.0, 1.0) shouldBe -50.0
      NightMode.curve(-33.0, 1.0) shouldBe -33.0 +- 1e-12
      NightMode.curve(-10.0, 1.0) shouldBe (-30.0 + 20.0 / 4.0) +- 1e-12
      NightMode.curve(0.0, 1.0) shouldBe -22.5 +- 1e-12
      NightMode.curve(-27.0, 1.0) shouldBe -29.25 +- 1e-12
      // at the threshold, the knee's middle: a quarter of its width, times 1 - 1/ratio, off
      NightMode.curve(-30.0, 1.0) shouldBe (-30.0 - 0.75 * 6.0 / 8.0) +- 1e-12
      // smooth and rising through the knee
      val levels = (-400 to 0).map(_ / 10.0)
      val outs = levels.map(NightMode.curve(_, 1.0))
      outs.zip(outs.tail).foreach { case (a, b) => b should be > a }
      (0 to 40).foreach(i => NightMode.curve(-60.0 + i * 1.5, 0.0) shouldBe (-60.0 + i * 1.5))
    }
    "make up what the curve takes off the reference, so that is left as it was" in {
      NightMode.makeup(0.0) shouldBe 0.0
      NightMode.makeup(1.0) shouldBe 7.5 +- 1e-12
      List(0.25, 0.5, 0.75, 1.0).foreach { a =>
        (NightMode.curve(NightMode.Reference, a) + NightMode.makeup(a)) shouldBe NightMode.Reference +- 1e-12
      }
    }
  }

  "Night mode" should {
    "settle every level where its curve and makeup put it, within 0.1 dB" in {
      List(-60.0, -45.0, -30.0, -20.0, -10.0, 0.0).foreach { level =>
        val n = new NightMode(2, rate)
        n.level(1.0)
        val out = run(n, frames(2, rate)((_, f) => square(f, level)))
        val last = math.abs(out(out.length - 1).toDouble)
        withClue(level.toString + " dBFS: ") {
          20.0 * math.log10(last) shouldBe (NightMode.curve(level, 1.0) + NightMode.makeup(1.0)) +- 0.1
        }
      }
    }
    "bring the quiet up and the loud down: a 60 dB range into 37.5" in {
      def out(level: Double): Double = {
        val n = new NightMode(1, rate)
        n.level(1.0)
        val o = run(n, frames(1, rate)((_, f) => square(f, level)))
        20.0 * math.log10(math.abs(o(o.length - 1).toDouble))
      }
      out(-45.0) shouldBe -37.5 +- 0.1
      out(-20.0) shouldBe -20.0 +- 0.1
      out(0.0) shouldBe -15.0 +- 0.1
      (out(0.0) - out(-60.0)) shouldBe 37.5 +- 0.1
    }
    "come down to a burst within about its attack, and back over its release" in {
      val n = new NightMode(2, rate)
      n.level(1.0)
      gains(n, -45.0, rate * 2)
      val quiet = n.gainDb
      quiet shouldBe 7.5 +- 0.01
      val loud = gains(n, 0.0, rate / 2)
      val settled = loud.last
      settled shouldBe -15.0 +- 0.05
      def fraction(g: Double, from: Double, to: Double): Double = (g - from) / (to - from)
      // the level smoothed over 5 ms and the gain following over 5 ms: two thirds of the way in about a hundredth
      val attack63 = loud.indexWhere(g => fraction(g, quiet, settled) >= 1.0 - math.exp(-1.0))
      (attack63.toDouble / rate) should (be >= 0.005 and be <= 0.012)
      (loud.indexWhere(g => fraction(g, quiet, settled) >= 0.95).toDouble / rate) should be <= 0.03
      fraction(loud(rate / 1000), quiet, settled) should be < 0.2
      // back to quiet: the level falls past the knee within a few ms, and the gain follows over its 200
      val back = gains(n, -45.0, rate * 2)
      val release63 = back.indexWhere(g => fraction(g, settled, quiet) >= 1.0 - math.exp(-1.0))
      (release63.toDouble / rate) shouldBe (NightMode.ReleaseSeconds + 0.01) +- 0.015
      back.last shouldBe quiet +- 0.01
    }
    "move every channel together, by the loudest of them" in {
      List(2, 6, 8).foreach { channels =>
        val n = new NightMode(channels, rate)
        n.level(1.0)
        // a burst on the last channel alone, every other channel quiet
        val in = frames(channels, rate)((c, f) =>
          if (c == channels - 1) square(f, if (f > rate / 2) 0.0 else -45.0) else square(f, -45.0))
        val out = run(n, in.clone())
        (0 until rate by 97).foreach { f =>
          val g = out(f * channels).toDouble / in(f * channels)
          (1 until channels).foreach(c => (out(f * channels + c).toDouble / in(f * channels + c)) shouldBe g +- 1e-5)
        }
        // the quiet channels came down with the burst
        val quietOut = math.abs(out((rate - 1) * channels).toDouble)
        20.0 * math.log10(quietOut) shouldBe -60.0 +- 0.2
      }
    }
    "leave every sample exactly as it was at 0" in {
      List(1, 2, 6, 8).foreach { channels =>
        val n = new NightMode(channels, rate)
        val in = frames(channels, 4800)((c, f) => square(f, -6.0 * c))
        val out = run(n, in.clone())
        out.toList shouldBe in.toList
      }
      // and again once it has glided back
      val n = new NightMode(2, rate)
      n.level(1.0)
      run(n, frames(2, rate)((_, f) => square(f, 0.0)))
      n.level(0.0)
      run(n, frames(2, rate * 3)((_, f) => square(f, 0.0)))
      n.quiet shouldBe true
      val in = frames(2, 4800)((_, f) => square(f, -3.0))
      run(n, in.clone()).toList shouldBe in.toList
    }
    "glide to a new amount rather than step to it" in {
      val n = new NightMode(1, rate)
      n.level(1.0)
      gains(n, -45.0, 1)
      n.amount should be < 0.01
      gains(n, -45.0, (rate * Downmix.GlideSeconds).toInt - 1)
      n.amount shouldBe 0.63 +- 0.02
      gains(n, -45.0, rate)
      n.amount shouldBe 1.0
      // the gain rises smoothly: no step between frames of more than a hundredth of a dB
      val m = new NightMode(1, rate)
      m.level(1.0)
      val g = gains(m, -45.0, rate / 2)
      g.zip(g.tail).map { case (a, b) => math.abs(b - a) }.max should be < 0.01
      // turned off, neither dipping under where it was nor swelling over it, on a loud passage and a quiet one
      List(0.0 -> -15.0, -45.0 -> 7.5).foreach { case (level, settled) =>
        val o = new NightMode(1, rate)
        o.level(1.0)
        gains(o, level, rate * 2).last shouldBe settled +- 0.05
        o.level(0.0)
        val off = gains(o, level, rate * 3)
        val all = settled +: off.toSeq
        all.zip(all.tail).foreach { case (a, b) =>
          if (settled < 0.0) b should be >= a - 1e-9 else b should be <= a + 1e-9
        }
        o.quiet shouldBe true
      }
    }
    "take only 0 to 1, and what is not a number as 0" in {
      NightMode.clamp(1.5) shouldBe 1.0
      NightMode.clamp(-0.5) shouldBe 0.0
      NightMode.clamp(Double.NaN) shouldBe 0.0
    }
  }
}
