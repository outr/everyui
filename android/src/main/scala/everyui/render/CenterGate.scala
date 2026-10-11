package everyui.render

import androidx.media3.common.{Format, MimeTypes}
import androidx.media3.exoplayer.audio.{AudioOffloadSupport, AudioSink, ForwardingAudioSink}
import androidx.media3.exoplayer.mediacodec.{MediaCodecSelector, MediaCodecUtil}
import everyui.audio.CenterRoute

/** The player's audio sink, but for sound the output would take still encoded (AC-3, E-AC-3 and its Atmos, DTS,
  * TrueHD, passed through to an AV receiver or a television to decode) while the centre mix is at a level other than
  * 0 ([[force]]), or while a listening aid is above 0 ([[aids]], [[ListeningProcessor]]): then it says it cannot take
  * it, so the renderer decodes it here, to PCM in all its channels, and [[CenterMixer]] can reach its centre
  * ([[CenterRoute.decodeHere]]) and the aids its samples. A format with no decoder for it here passes as ever. With all
  * three at 0 nothing is refused, so a receiver gets its bitstream exactly as before.
  *
  * What the sink is asked changes nothing already playing: [[CenterAudioRenderer]] watches [[changes]] and decides
  * again at once. */
class CenterGate(sink: AudioSink) extends ForwardingAudioSink(sink) {
  @volatile private var level = 0.0
  /** Counted up each time forcing turns on or off, for the renderer to see. */
  @volatile var changes: Int = 0

  /** The centre's level: anything but 0 has sound the output takes encoded decoded here instead. */
  def force(db: Double): Unit = {
    val was = forcing
    level = db
    if (forcing != was) changes += 1
  }

  /** Whether a listening aid is above 0: it has sound the output takes encoded decoded here too, as the centre does. */
  def aids(on: Boolean): Unit = {
    val was = forcing
    aided = on
    if (forcing != was) changes += 1
  }
  @volatile private var aided = false

  /** Whether sound the output takes encoded is decoded here, where it can be. */
  def forcing: Boolean = aided || CenterRoute.decodeHere(level, passes = true, decodable = true)

  /** Whether `format` would go out encoded, as it came, were nothing refused. Asked on the player's thread. */
  def passes(format: Format): Boolean = {
    val can = !MimeTypes.AUDIO_RAW.equals(format.sampleMimeType) && super.supportsFormat(format)
    val mime = String.valueOf(format.sampleMimeType)
    if (can != passed.contains(mime)) passed = if (can) passed + mime else passed - mime
    can
  }

  /** The kinds of sound (MIME types) last found to go out encoded were nothing refused, for any thread to read. */
  @volatile var passed: Set[String] = Set.empty

  /** Whether `format`, which the output would take as it is, is decoded here now. */
  def refuses(format: Format): Boolean = forcing && passes(format) && CenterGate.decodable(format)

  override def supportsFormat(format: Format): Boolean = !refuses(format) && super.supportsFormat(format)

  override def getFormatSupport(format: Format): Int =
    if (refuses(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)

  override def getFormatOffloadSupport(format: Format): AudioOffloadSupport =
    if (refuses(format)) AudioOffloadSupport.DEFAULT_UNSUPPORTED else super.getFormatOffloadSupport(format)
}

object CenterGate {
  private val known = new java.util.concurrent.ConcurrentHashMap[String, java.lang.Boolean]()

  /** Whether the device has a decoder for `format` (an E-AC-3 decoder for E-AC-3 with Atmos, which it decodes to its
    * bed), asked once for each kind. Plain PCM needs none. */
  def decodable(format: Format): Boolean =
    if (format.sampleMimeType == null) false
    else if (MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)) true
    else {
      val key = format.sampleMimeType
      val was = known.get(key)
      if (was != null) was.booleanValue
      else {
        val found = try {
          !MediaCodecUtil.getDecoderInfosSoftMatch(MediaCodecSelector.DEFAULT, format, false, false).isEmpty
        } catch {
          case _: Throwable => false
        }
        known.put(key, java.lang.Boolean.valueOf(found))
        found
      }
    }

  /** What a format is called in the log: "E-AC-3", "DTS-HD", or its MIME type. */
  def named(format: Format): String = {
    val mime = Option(format.sampleMimeType).getOrElse("")
    val name =
      if (mime == MimeTypes.AUDIO_AC3) "AC-3"
      else if (mime == MimeTypes.AUDIO_E_AC3) "E-AC-3"
      else if (mime == MimeTypes.AUDIO_E_AC3_JOC) "E-AC-3 with Atmos"
      else if (mime == MimeTypes.AUDIO_AC4) "AC-4"
      else if (mime == MimeTypes.AUDIO_TRUEHD) "TrueHD"
      else if (mime == MimeTypes.AUDIO_DTS) "DTS"
      else if (mime == MimeTypes.AUDIO_DTS_HD) "DTS-HD"
      else if (mime == MimeTypes.AUDIO_DTS_EXPRESS) "DTS Express"
      else if (mime == MimeTypes.AUDIO_DTS_X) "DTS:X"
      else if (mime == MimeTypes.AUDIO_AAC) "AAC"
      else if (mime == MimeTypes.AUDIO_RAW) "PCM"
      else mime
    if (format.channelCount > 0) name + " in " + format.channelCount.toString + " channels" else name
  }
}
