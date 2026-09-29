package everyui.audio

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Voice audio's arithmetic, apart from any device: PCM's bytes and levels, resampling a stream a block at a time,
  * cutting it into chunks, a capture fed by hand, and a player's queue and schedule. */
class AudioSpec extends AnyWordSpec with Matchers {
  private def sine(frequency: Double, rate: Int, n: Int, amplitude: Double = 0.5): Array[Float] =
    Array.tabulate(n)(i => (math.sin(2.0 * math.Pi * frequency * i / rate) * amplitude).toFloat)

  /** `block` fed through `r` in pieces of `size`. */
  private def fed(r: Resampler, block: Array[Float], size: Int): Array[Float] =
    block.grouped(size).flatMap(b => r.push(b)).toArray

  "Pcm" should {
    "turn samples into little-endian bytes and back" in {
      val samples = Array[Short](0, 1, -1, 32767, -32768, 0x1234)
      val bytes = Pcm.toBytes(samples)
      bytes.take(4).toList shouldBe List[Byte](0, 0, 1, 0)
      bytes.slice(10, 12).toList shouldBe List[Byte](0x34, 0x12)
      Pcm.fromBytes(bytes).toList shouldBe samples.toList
      Pcm.fromBytes(bytes :+ 7.toByte).length shouldBe samples.length
    }
    "clip floats to 16 bits" in {
      Pcm.toShort(1.0f) shouldBe 32767
      Pcm.toShort(-1.0f) shouldBe -32768
      Pcm.toShort(3.0f) shouldBe 32767
      Pcm.toShort(-3.0f) shouldBe -32768
      Pcm.toShort(0.0f) shouldBe 0
    }
    "measure the level as the root mean square" in {
      Pcm.level(Array.fill[Short](100)(0)) shouldBe 0.0
      Pcm.level(Array.empty[Short]) shouldBe 0.0
      Pcm.level(Array.fill[Short](100)(16384)) shouldBe 0.5 +- 1e-9
      Pcm.level(Pcm.tone(440, 1.0, 16000, 1.0)) shouldBe (1.0 / math.sqrt(2.0)) +- 0.001
    }
  }

  "Resampler" should {
    "give a third as many samples from 48 kHz to 16 kHz, whatever the blocks" in {
      val in = sine(440, 48000, 48000)
      for (size <- List(128, 1000, 4096, 48000)) fed(new Resampler(48000, 16000), in, size).length shouldBe 16000
    }
    "keep the count right at an uneven ratio across blocks" in {
      val in = sine(440, 44100, 44100 * 2)
      fed(new Resampler(44100, 16000), in, 441).length shouldBe 32000 +- 1
      fed(new Resampler(16000, 24000), sine(440, 16000, 16000), 333).length shouldBe 24000 +- 1
    }
    "keep a tone its level and its shape, and the same with any block size" in {
      val in = sine(440, 48000, 48000)
      val whole = new Resampler(48000, 16000).push(in)
      val pieces = fed(new Resampler(48000, 16000), in, 128)
      pieces.zip(whole).foreach { case (a, b) => a shouldBe b +- 1e-5f }
      // the average over each third is a 440 Hz sine at 16 kHz, barely quieter (a box filter's loss at 440 Hz)
      val expected = sine(440, 16000, 16000)
      whole.drop(1).zip(expected.drop(1)).map { case (a, b) => math.abs(a - b) }.max should be < 0.03f
    }
    "take out much of what would alias going down" in {
      // 15 kHz folds to 1 kHz at 16 kHz; averaged over threes it comes out far quieter than it went in
      val out = new Resampler(48000, 16000).push(sine(15000, 48000, 48000))
      val rms = math.sqrt(out.map(v => v * v).sum / out.length)
      rms should be < 0.2
    }
    "interpolate going up, joining blocks without a step" in {
      val in = sine(200, 16000, 16000)
      val out = fed(new Resampler(16000, 48000), in, 100)
      val expected = sine(200, 48000, out.length)
      out.zip(expected).map { case (a, b) => math.abs(a - b) }.max should be < 0.01f
    }
    "pass the same rate through" in {
      val in = Array(0.1f, 0.2f, 0.3f)
      new Resampler(16000, 16000).push(in).toList shouldBe in.toList
    }
  }

  "Chunker" should {
    "cut a stream into whole chunks however it arrives, with the rest last" in {
      val c = new Chunker(4)
      c.push(Array(0.1f, 0.1f)) shouldBe Nil
      c.waiting shouldBe 2
      val two = c.push(Array.fill(7)(0.5f))
      two.map(_.length) shouldBe List(4, 4)
      c.waiting shouldBe 1
      c.rest().map(_.length) shouldBe Some(1)
      c.rest() shouldBe None
    }
    "keep the samples in order" in {
      val c = new Chunker(3)
      val out = c.push(Array(0.0f, 0.25f, 0.5f)) ++ c.push(Array(0.75f, 1.0f, -1.0f))
      out.flatMap(_.toList) shouldBe List(0, 8191, 16383, 24575, 32767, -32768).map(_.toShort)
    }
  }

  "A capture" should {
    "resample and chunk what the platform hears, with levels, and end once with the rest" in {
      var chunks = List.empty[AudioChunk]
      var ends = List.empty[CaptureEnd]
      var halted = 0
      val c = new Capture(16000, 2048, ch => chunks = chunks :+ ch, why => ends = ends :+ why)
      c.begin(() => halted += 1)
      c.heard(Array.fill(480)(0.5f))   // before it opened: nothing to resample with, dropped
      c.opened(48000)
      c.deviceRate shouldBe Some(48000)
      sine(440, 48000, 48000).grouped(1024).foreach(c.heard)
      chunks.map(_.samples.length) shouldBe List.fill(7)(2048)
      chunks.foreach { ch =>
        ch.sampleRate shouldBe 16000
        ch.level shouldBe 0.35 +- 0.01
      }
      c.stop()
      halted shouldBe 1
      chunks.length shouldBe 8
      chunks.last.samples.length shouldBe 16000 - 7 * 2048
      ends shouldBe List(CaptureEnd.Stopped)
      c.end(CaptureEnd.Lost)
      c.heard(Array.fill(4096)(0.5f))
      c.stop()
      ends shouldBe List(CaptureEnd.Stopped)
      chunks.length shouldBe 8
      c.running shouldBe false
    }
    "stop the platform's recording when it was stopped before the platform answered" in {
      var halted = 0
      val c = new Capture(16000, 2048, _ => (), _ => ())
      c.end(CaptureEnd.Denied)
      c.begin(() => halted += 1)
      halted shouldBe 1
    }
  }

  "A playback queue" should {
    "drain once, after the last chunk queued" in {
      val q = new PlaybackQueue
      val a = q.queue()
      val b = q.queue()
      q.playing shouldBe true
      q.ended(a) shouldBe false
      q.ended(b) shouldBe true
      q.ended(b) shouldBe false
      q.playing shouldBe false
    }
    "not drain for chunks flushed by a stop, even when the ends come after new chunks" in {
      val q = new PlaybackQueue
      val a = q.queue()
      q.stop()
      q.playing shouldBe false
      q.ended(a) shouldBe false
      val b = q.queue()
      q.ended(a) shouldBe false
      q.playing shouldBe true
      q.ended(b) shouldBe true
    }
  }

  "A playback schedule" should {
    "put chunks back to back, and start after a lead when it has run dry" in {
      val s = new PlaybackSchedule(0.05)
      s.start(10.0, 0.5) shouldBe 10.05
      s.start(10.1, 0.5) shouldBe 10.55
      s.ahead(10.1) shouldBe 0.95 +- 1e-9
      // arriving after the queue ran out: a gap is unavoidable, and it starts a lead from now
      s.start(12.0, 0.5) shouldBe 12.05
      s.reset()
      s.ahead(12.0) shouldBe 0.0
      s.start(12.1, 0.5) shouldBe 12.15 +- 1e-9
    }
  }
}
