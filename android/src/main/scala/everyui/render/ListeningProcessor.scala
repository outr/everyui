package everyui.render

import androidx.media3.common.C
import androidx.media3.common.audio.{AudioProcessor, BaseAudioProcessor}
import everyui.audio.ListeningAids

import java.nio.{ByteBuffer, ByteOrder}

/** The listening aids (MediaSupport.voiceClarity and nightMode, [[ListeningAids]]) in the player's audio path, right
  * after the centre's mix ([[CenterMixer]]), so they hear what it made of the sound: two channels where it folded, all
  * of them where it lifted the centre or left it alone.
  *
  * It takes any PCM, 16-bit or float, in any number of channels, and is in the path whenever the sound is PCM, so
  * either aid is heard at once when it is first asked for; with both at 0 (and nothing left to glide or give back) it
  * copies every byte through as it came. Sound sent out still encoded never reaches it: [[CenterGate]] has it decoded
  * here while either aid is above 0. [[active]] says whether it is in the path.
  *
  * A second after each change of either aid, and again two seconds later once night mode has settled, it says how
  * loud the sound went in and came out (`media: listening aids …`). Fed on the player's audio thread. */
class ListeningProcessor extends BaseAudioProcessor {
  /** Voice clarity's strength and night mode's amount, 0 to 1: read at each buffer, so a change is heard at once. */
  @volatile var clarity: Double = 0.0
  @volatile var night: Double = 0.0
  /** Whether it is in the path for what it was last set up for: PCM, said as it is set up. */
  @volatile var active: Boolean = false

  private var aids: Option[ListeningAids] = None
  private var samples: Array[Float] = new Array[Float](0)
  private var told = (Double.NaN, Double.NaN)
  private var counted = 0L
  private var tellAt = 0L
  // said once a second after a change, and again two after that, settled
  private var tells = 0

  override protected def onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = {
    active = format.encoding == C.ENCODING_PCM_16BIT || format.encoding == C.ENCODING_PCM_FLOAT
    if (active) format else AudioProcessor.AudioFormat.NOT_SET
  }

  // a seek keeps what it had; a stream of another shape is a new one
  override protected def onFlush(metadata: AudioProcessor.StreamMetadata): Unit =
    if (outputAudioFormat == AudioProcessor.AudioFormat.NOT_SET) aids = None
    else if (!aids.exists(a => a.channels == inputAudioFormat.channelCount && a.rate == inputAudioFormat.sampleRate)) {
      aids = Some(new ListeningAids(inputAudioFormat.channelCount, inputAudioFormat.sampleRate))
      told = (Double.NaN, Double.NaN)
    }

  override protected def onReset(): Unit = {
    aids = None
    active = false
  }

  override def queueInput(buffer: ByteBuffer): Unit = if (buffer.hasRemaining) aids match {
    case Some(a) =>
      val now = (clarity, night)
      if (now != told) {
        a.voiceClarity(now._1)
        a.nightMode(now._2)
        told = now
        a.heard()
        tellAt = counted + a.rate
        tells = 2
      }
      val size = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
      counted += buffer.remaining() / (size * a.channels)
      if (a.quiet) {
        // nothing to do: every byte as it came
        val out = replaceOutputBuffer(buffer.remaining())
        out.put(buffer)
        out.flip()
      }
      else process(a, buffer)
      if (tellAt > 0L && counted >= tellAt) {
        tells -= 1
        tellAt = if (tells > 0) counted + 2L * a.rate else 0L
        Platform.log("media: listening aids at voice clarity " + ListeningAids.said(now._1) + ", night mode " +
          ListeningAids.said(now._2) + ": " + (if (a.quiet) "every sample passed as it came" else a.heard()))
      }
    case None =>
      val out = replaceOutputBuffer(buffer.remaining())
      out.put(buffer)
      out.flip()
  }

  private def process(a: ListeningAids, buffer: ByteBuffer): Unit = {
    val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
    val size = if (float) 4 else 2
    val in = buffer.order(ByteOrder.nativeOrder())
    val frames = in.remaining() / (size * a.channels)
    val n = frames * a.channels
    if (samples.length < n) samples = new Array[Float](n)
    var i = 0
    while (i < n) {
      samples(i) = if (float) in.getFloat() else in.getShort() / 32768.0f
      i += 1
    }
    // a partial frame left over is dropped with the rest of what was read
    in.position(in.limit())
    a.process(samples, frames)
    val out = replaceOutputBuffer(n * size)
    i = 0
    while (i < n) {
      if (float) out.putFloat(samples(i))
      else out.putShort(math.max(-32768, math.min(32767, math.round(samples(i) * 32768.0f))).toShort)
      i += 1
    }
    out.flip()
  }
}
