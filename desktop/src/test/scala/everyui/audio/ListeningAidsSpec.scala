package everyui.audio

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The listening aids together (voice clarity, then night mode, then the ceiling): nothing touched while both are 0,
  * in any number of channels, nothing past the ceiling at their strongest on the loudest input, and what it says it
  * heard. */
class ListeningAidsSpec extends AnyWordSpec with Matchers {
  private val rate = 48000

  private def frames(channels: Int, count: Int)(sample: (Int, Int) => Double): Array[Float] =
    Array.tabulate(channels * count)(i => sample(i % channels, i / channels).toFloat)

  private def sine(frame: Int, amplitude: Double, hz: Double): Double =
    amplitude * math.sin(2.0 * math.Pi * hz * frame / rate)

  private def peak(samples: Array[Float]): Double = samples.map(x => math.abs(x.toDouble)).max

  "Listening aids" should {
    "leave every sample exactly as it was with both at 0, in mono, stereo, 5.1 and 7.1" in {
      List(1, 2, 6, 8).foreach { channels =>
        val aids = new ListeningAids(channels, rate)
        aids.quiet shouldBe true
        val in = frames(channels, rate)((c, f) => sine(f, 1.0, 50.0 * (c + 1)))
        val out = in.clone()
        aids.process(out, rate)
        out.toList shouldBe in.toList
      }
    }
    "pass by exactly again once both have glided back to 0 and the limiter has given back its gain" in {
      val aids = new ListeningAids(2, rate)
      aids.voiceClarity(1.0)
      aids.nightMode(1.0)
      aids.process(frames(2, rate)((_, f) => sine(f, 1.0, 2800.0)), rate)
      aids.ceiling.limiting should be < 1.0
      aids.voiceClarity(0.0)
      aids.nightMode(0.0)
      aids.process(frames(2, rate * 3)((_, f) => sine(f, 0.1, 2800.0)), rate * 3)
      aids.quiet shouldBe true
      val in = frames(2, 4800)((_, f) => sine(f, 1.0, 2800.0))
      val out = in.clone()
      aids.process(out, 4800)
      out.toList shouldBe in.toList
    }
    "never go past the ceiling at their strongest, however loud the input, in every channel count" in {
      List(1, 2, 6, 8).foreach { channels =>
        val random = new scala.util.Random(channels)
        val aids = new ListeningAids(channels, rate)
        aids.voiceClarity(1.0)
        aids.nightMode(1.0)
        val noise = frames(channels, rate)((_, _) => if (random.nextBoolean()) 1.0 else -1.0)
        aids.process(noise, rate)
        withClue(channels.toString + " channels of full-scale noise: ") {
          peak(noise) should be <= Downmix.Ceiling + 1e-6
        }
        // full-scale sines where the lift is most, in step, after a quiet passage night mode has lifted
        aids.process(frames(channels, rate)((_, f) => sine(f, 0.003, 2800.0)), rate)
        val sines = frames(channels, rate)((_, f) => sine(f, 1.0, 2800.0))
        aids.process(sines, rate)
        withClue(channels.toString + " channels of full-scale sines: ") {
          peak(sines) should be <= Downmix.Ceiling + 1e-6
          peak(sines) should be >= Downmix.Ceiling - 0.05
        }
      }
    }
    "run voice clarity before night mode" in {
      // a tone at the lift's centre settles night mode's gain from the lifted level, 6 dB up
      val aids = new ListeningAids(1, rate)
      aids.voiceClarity(1.0)
      aids.nightMode(1.0)
      val out = frames(1, rate * 2)((_, f) => sine(f, 0.1, 2800.0))
      aids.process(out, rate * 2)
      // in at -23 dBFS of root mean square, lifted to -17, then the curve and the makeup
      val lifted = 20.0 * math.log10(0.1 / math.sqrt(2.0)) + 6.0
      aids.night.gainDb shouldBe (NightMode.curve(lifted, 1.0) - lifted + NightMode.makeup(1.0)) +- 0.15
    }
    "say how loud it went in and came out, what night mode did and what was limited" in {
      val aids = new ListeningAids(2, rate)
      aids.nightMode(1.0)
      def quiet(): Unit = aids.process(frames(2, rate)((_, f) => if ((f / 24) % 2 == 0) 0.01 else -0.01), rate)
      // the lift comes over night mode's release
      quiet()
      quiet()
      aids.heard() should endWith("night mode +0.0 to +7.5 dB, limited 0.0 dB")
      // counted afresh, settled
      quiet()
      aids.heard() shouldBe "in -40.0 dBFS, out -32.5 dBFS, night mode +7.5 to +7.5 dB, limited 0.0 dB"
      aids.heard() should startWith("in -120.0 dBFS, out -120.0 dBFS")
    }
  }
}
