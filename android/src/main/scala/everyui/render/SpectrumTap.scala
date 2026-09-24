package everyui.render

import androidx.media3.common.C
import androidx.media3.common.audio.{AudioProcessor, BaseAudioProcessor}

import java.nio.{ByteBuffer, ByteOrder}

/** A listener in the player's audio path: the decoded sound passes through untouched, and on its way a mono window of
  * it is taken, turned into [[SpectrumTap.Bands]] levels, and handed to `heard` about thirty times a second — the live
  * spectrum a visualizer draws, as the Flutter app's player took it (its NaboFft and SpectrumAudioProcessor).
  *
  * Only 16-bit PCM is listened to, which is what Media3 decodes to; anything else passes by without it (and the
  * visualizer draws its own motion instead). `heard` is called on the audio thread. */
class SpectrumTap(heard: Array[Double] => Unit) extends BaseAudioProcessor {
  private val frame = new Array[Double](SpectrumTap.Size)
  private var fill = 0
  private var rate = 48000
  private var channels = 2
  private var last = 0L

  override protected def onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = {
    rate = format.sampleRate
    channels = math.max(1, format.channelCount)
    fill = 0
    if (format.encoding == C.ENCODING_PCM_16BIT) format else AudioProcessor.AudioFormat.NOT_SET
  }

  override def queueInput(input: ByteBuffer): Unit = {
    val remaining = input.remaining()
    if (remaining > 0) {
      listen(input.duplicate().order(ByteOrder.LITTLE_ENDIAN))
      val out = replaceOutputBuffer(remaining)
      out.put(input)
      out.flip()
    }
  }

  override protected def onReset(): Unit = fill = 0

  private def listen(buffer: ByteBuffer): Unit = {
    val samples = buffer.remaining() / 2
    var i = 0
    while (i + channels <= samples) {
      var sum = 0.0
      var c = 0
      while (c < channels) {
        sum += buffer.getShort() / 32768.0
        c += 1
      }
      frame(fill) = sum / channels
      fill += 1
      i += channels
      if (fill >= SpectrumTap.Size) {
        val now = System.currentTimeMillis()
        if (now - last >= 33L) {
          last = now
          heard(SpectrumTap.bands(frame, rate))
        }
        // half the window kept, so one frame runs on into the next
        System.arraycopy(frame, SpectrumTap.Size / 2, frame, 0, SpectrumTap.Size / 2)
        fill = SpectrumTap.Size / 2
      }
    }
  }
}

object SpectrumTap {
  val Size: Int = 1024
  val Bands: Int = 24
  private val low = 60.0
  private val high = 3500.0
  private lazy val window: Array[Double] =
    Array.tabulate(Size)(i => 0.5 - 0.5 * math.cos(2.0 * math.Pi * i / (Size - 1)))

  /** A window of mono samples (-1 to 1) as [[Bands]] levels from 0 to 1, spaced by pitch from 60 Hz to 3.5 kHz, a
    * little lifted towards the top so quiet treble still shows: about -70 dB to -6 dB made 0 to 1. The same shape as
    * the server's trivia spectrum, so a visualizer looks alike fed by either. */
  def bands(frame: Array[Double], sampleRate: Int): Array[Double] = {
    val re = new Array[Double](Size)
    val im = new Array[Double](Size)
    var i = 0
    while (i < Size) {
      re(i) = frame(i) * window(i)
      i += 1
    }
    fft(re, im)
    val half = Size / 2
    val bin = sampleRate.toDouble / Size
    val ratio = high / low
    Array.tabulate(Bands) { b =>
      val from = math.max(1, math.min(half - 1, (low * math.pow(ratio, b.toDouble / Bands) / bin).toInt))
      val to = math.max(from + 1, math.min(half, (low * math.pow(ratio, (b + 1).toDouble / Bands) / bin).toInt))
      var sum = 0.0
      var k = from
      while (k < to) {
        val magnitude = math.hypot(re(k), im(k)) / half
        sum += magnitude * magnitude
        k += 1
      }
      val rms = math.sqrt(sum / (to - from))
      val db = 20.0 * math.log10(rms + 1e-9)
      val level = math.max(0.0, math.min(1.0, (db + 70.0) / 64.0)) * (0.85 + 0.15 * b.toDouble / Bands)
      math.max(0.0, math.min(1.0, level))
    }
  }

  /** In place, radix 2: `re` and `im` a power of two long, `im` all zero for a real signal. */
  private def fft(re: Array[Double], im: Array[Double]): Unit = {
    val n = re.length
    var j = 0
    var i = 1
    while (i < n) {
      var bit = n >> 1
      while ((j & bit) != 0) {
        j ^= bit
        bit >>= 1
      }
      j |= bit
      if (i < j) {
        val r = re(i); re(i) = re(j); re(j) = r
        val m = im(i); im(i) = im(j); im(j) = m
      }
      i += 1
    }
    var len = 2
    while (len <= n) {
      val angle = -2.0 * math.Pi / len
      val wr = math.cos(angle)
      val wi = math.sin(angle)
      var start = 0
      while (start < n) {
        var cr = 1.0
        var ci = 0.0
        var k = 0
        while (k < len / 2) {
          val a = start + k
          val b = a + len / 2
          val vr = re(b) * cr - im(b) * ci
          val vi = re(b) * ci + im(b) * cr
          re(b) = re(a) - vr
          im(b) = im(a) - vi
          re(a) = re(a) + vr
          im(a) = im(a) + vi
          val next = cr * wr - ci * wi
          ci = cr * wi + ci * wr
          cr = next
          k += 1
        }
        start += len
      }
      len <<= 1
    }
  }
}
