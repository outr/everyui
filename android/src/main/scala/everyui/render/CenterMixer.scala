package everyui.render

import android.media.{AudioDeviceInfo, AudioManager}
import androidx.media3.common.C
import androidx.media3.common.audio.{AudioProcessor, BaseAudioProcessor}
import everyui.audio.Downmix

import java.nio.{ByteBuffer, ByteOrder}

/** A film's surround sound folded to two channels here, in the player's audio path, rather than by Android after it,
  * so its centre can be heard at the level asked for ([[Downmix]], MediaSupport.centerMix): in at `db` decibels over
  * the usual, changed as it plays.
  *
  * It takes the sound where Media3 hands it on, decoded and still in all its channels (the decoder is asked for as
  * many as there are, and the sink's processors come before its AudioTrack), as 16-bit or float PCM. It folds only a
  * stream with a centre among more than two channels, and only where what is playing goes out as two: a television's
  * speakers, headphones, a phone. Where the output plays every channel itself (an AV receiver over HDMI taking 5.1),
  * the stream passes by as it is, and so it does everywhere else; and sound sent out still encoded (AC-3 or E-AC-3
  * passed through to a receiver) never comes this way at all. [[active]] says whether it is folding.
  *
  * Each time it starts folding, and each time `db` changes, it says a second later how loud the centre went in and
  * each side came out (`media: centre mix …`). Fed on the player's audio thread. */
class CenterMixer extends BaseAudioProcessor {
  /** The centre's level over the usual, in dB: read at each buffer, so a change is heard at once. */
  @volatile var db: Double = 0.0
  /** Whether it folds what it was last set up for: said as it is set up, before the AudioTrack is made for it. */
  @volatile var active: Boolean = false

  private var fold: Option[Downmix] = None
  private var input: Array[Float] = new Array[Float](0)
  private var output: Array[Float] = new Array[Float](0)
  private var told = Double.NaN
  private var counted = 0L
  private var tellAt = 0L

  override protected def onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = {
    val pcm = format.encoding == C.ENCODING_PCM_16BIT || format.encoding == C.ENCODING_PCM_FLOAT
    if (!pcm || !Downmix.applies(format.channelCount)) {
      active = false
      AudioProcessor.AudioFormat.NOT_SET
    }
    else {
      val (kind, out) = CenterMixer.output()
      if (out == 2) {
        active = true
        Platform.log("media: centre mix: " + format.channelCount.toString + " channels (" +
          Downmix.roles(format.channelCount) + ") at " + format.sampleRate.toString + " Hz folded to stereo for " + kind)
        new AudioProcessor.AudioFormat(format.sampleRate, 2, format.encoding)
      }
      else {
        active = false
        Platform.log("media: centre mix: " + format.channelCount.toString + " channels left as they are, for " + kind +
          (if (out == 0) ", whose channels are not known" else " of " + out.toString + " channels"))
        AudioProcessor.AudioFormat.NOT_SET
      }
    }
  }

  // a seek keeps the fold it had; a stream of another shape is a new one, and said again
  override protected def onFlush(metadata: AudioProcessor.StreamMetadata): Unit =
    if (outputAudioFormat == AudioProcessor.AudioFormat.NOT_SET) fold = None
    else if (!fold.exists(f => f.channels == inputAudioFormat.channelCount && f.rate == inputAudioFormat.sampleRate)) {
      fold = Some(new Downmix(inputAudioFormat.channelCount, inputAudioFormat.sampleRate))
      told = Double.NaN
      counted = 0L
    }

  override protected def onReset(): Unit = {
    fold = None
    active = false
  }

  override def queueInput(buffer: ByteBuffer): Unit = fold match {
    case Some(f) if buffer.hasRemaining =>
      val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
      val size = if (float) 4 else 2
      val in = buffer.order(ByteOrder.nativeOrder())
      val frames = in.remaining() / (size * f.channels)
      val samples = frames * f.channels
      if (input.length < samples) input = new Array[Float](samples)
      if (output.length < frames * 2) output = new Array[Float](frames * 2)
      var i = 0
      while (i < samples) {
        input(i) = if (float) in.getFloat() else in.getShort() / 32768.0f
        i += 1
      }
      // a partial frame left over is dropped with the rest of what was read
      in.position(in.limit())
      val now = db
      if (now != told) {
        f.center(now)
        told = now
        f.heard()
        tellAt = counted + f.rate
      }
      f.mix(input, frames, output)
      counted += frames
      if (tellAt > 0L && counted >= tellAt) {
        tellAt = 0L
        Platform.log("media: centre mix at " + Downmix.signed(now) + " dB: " + f.heard())
      }
      val out = replaceOutputBuffer(frames * 2 * size)
      var o = 0
      while (o < frames * 2) {
        if (float) out.putFloat(output(o))
        else out.putShort(math.round(output(o) * 32767.0f).toShort)
        o += 1
      }
      out.flip()
    case _ => ()
  }
}

object CenterMixer {
  // Android 12's kinds of output, by number: their names are not there to be read on Android 11
  private val BleHeadset = 26
  private val BleSpeaker = 27
  private val HdmiEarc = 29

  /** What the sound goes out through, and how many channels it plays there: 2 for speakers, headphones and a
    * Bluetooth or USB headset, what an HDMI (or ARC) sink says it takes as PCM, and 0 where that is not known. The
    * route is Android's own for media from Android 13; before it, a headset or Bluetooth connected is taken to be
    * where the sound goes, as Android sends it there, and HDMI otherwise. */
  def output(): (String, Int) = try {
    val manager = Host.activity.getSystemService(classOf[AudioManager])
    val routed =
      if (android.os.Build.VERSION.SDK_INT >= 33) {
        val attributes = new android.media.AudioAttributes.Builder()
          .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
        val list = manager.getAudioDevicesForAttributes(attributes)
        if (list.isEmpty) None else Some(list.get(0))
      }
      else {
        val all = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList
        all.find(d => stereo(d.getType) && d.getType != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
          .orElse(all.find(d => sink(d.getType))).orElse(all.headOption)
      }
    routed match {
      case None => ("an unknown output", 0)
      case Some(d) =>
        val kind = named(d.getType)
        if (stereo(d.getType)) (kind, 2) else (kind, pcmChannels(d))
    }
  } catch {
    case t: Throwable =>
      Platform.logError("media: centre mix: no output to ask", t)
      ("an unknown output", 0)
  }

  private def stereo(kind: Int): Boolean = kind == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
    kind == AudioDeviceInfo.TYPE_WIRED_HEADSET || kind == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
    kind == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || kind == AudioDeviceInfo.TYPE_USB_HEADSET ||
    kind == CenterMixer.BleHeadset || kind == CenterMixer.BleSpeaker

  private def sink(kind: Int): Boolean = kind == AudioDeviceInfo.TYPE_HDMI || kind == AudioDeviceInfo.TYPE_HDMI_ARC ||
    kind == CenterMixer.HdmiEarc

  private def named(kind: Int): String =
    if (kind == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "the speaker"
    else if (kind == AudioDeviceInfo.TYPE_HDMI) "HDMI"
    else if (kind == AudioDeviceInfo.TYPE_HDMI_ARC) "HDMI ARC"
    else if (kind == HdmiEarc) "HDMI eARC"
    else if (kind == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) "Bluetooth"
    else if (stereo(kind)) "a headset"
    else "output type " + kind.toString

  /** The most channels the device says it takes as PCM (its profiles from Android 12, its channel counts before), or
    * 0 where it says nothing. A Dolby or DTS profile is what it takes encoded, and does not count. */
  private def pcmChannels(d: AudioDeviceInfo): Int = {
    val profiled = if (android.os.Build.VERSION.SDK_INT >= 31) {
      // 16-bit, float, 24-bit packed and 32-bit PCM
      val pcm = Set(2, 4, 21, 22)
      val profiles = d.getAudioProfiles
      var most = 0
      var i = 0
      while (i < profiles.size()) {
        val p = profiles.get(i)
        if (pcm.contains(p.getFormat)) {
          p.getChannelMasks.foreach(m => most = math.max(most, Integer.bitCount(m)))
          p.getChannelIndexMasks.foreach(m => most = math.max(most, Integer.bitCount(m)))
        }
        i += 1
      }
      most
    } else 0
    if (profiled > 0) profiled
    else d.getChannelCounts.foldLeft(0)(math.max)
  }
}
