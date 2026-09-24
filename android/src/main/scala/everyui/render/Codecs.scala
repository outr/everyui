package everyui.render

import android.media.{AudioAttributes, AudioFormat, AudioTrack, MediaCodecList}

/** What this device can play, by the names a media server gives codecs ("hevc", "ac3", "dca"): a decoder for it, or
  * for sound a way to pass it through untouched to the television or the receiver.
  *
  * The second matters as much as the first. A SHIELD typically has no AC3 decoder at all: it bitstreams AC3 and DTS
  * to whatever is downstream, which is how every other player on the same box plays those files. A list of decoders
  * alone says it cannot play them, and a file it plays perfectly well gets sent for a transcode. */
object Codecs {
  private val mimes: Map[String, String] = Map(
    "h264" -> "video/avc", "avc" -> "video/avc",
    "hevc" -> "video/hevc", "h265" -> "video/hevc",
    "mpeg4" -> "video/mp4v-es", "msmpeg4" -> "video/mp4v-es",
    "mpeg2video" -> "video/mpeg2", "mpeg1video" -> "video/mpeg2",
    "vc1" -> "video/wvc1", "vp8" -> "video/x-vnd.on2.vp8",
    "vp9" -> "video/x-vnd.on2.vp9", "av1" -> "video/av01",
    "aac" -> "audio/mp4a-latm", "ac3" -> "audio/ac3", "eac3" -> "audio/eac3",
    "dca" -> "audio/vnd.dts", "dts" -> "audio/vnd.dts",
    "truehd" -> "audio/true-hd", "mlp" -> "audio/true-hd",
    "mp3" -> "audio/mpeg", "mp2" -> "audio/mpeg-L2",
    "flac" -> "audio/flac", "opus" -> "audio/opus", "vorbis" -> "audio/vorbis",
    "pcm_s16le" -> "audio/raw", "pcm" -> "audio/raw")

  /** Every name it can play, or nothing where the device would not say — which is read as able to play anything. */
  lazy val playable: Set[String] =
    try {
      val decoders = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos.toList
        .filterNot(_.isEncoder).flatMap(_.getSupportedTypes.toList).map(_.toLowerCase).toSet
      mimes.collect { case (name, mime) if decoders.contains(mime) => name }.toSet ++ passthrough
    }
    catch {
      case t: Throwable =>
        Platform.logError("codecs: the device would not list them", t)
        Set.empty
    }

  /** The sound formats the output takes as they are, for a television or a receiver to decode. */
  private def passthrough: Set[String] =
    if (android.os.Build.VERSION.SDK_INT < 29) Set.empty
    else {
      val attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()
      List(AudioFormat.ENCODING_AC3 -> List("ac3"), AudioFormat.ENCODING_E_AC3 -> List("eac3"),
        AudioFormat.ENCODING_DTS -> List("dca", "dts"), AudioFormat.ENCODING_DTS_HD -> List("dts_hd"),
        AudioFormat.ENCODING_DOLBY_TRUEHD -> List("truehd", "mlp")).flatMap { case (encoding, names) =>
        try {
          val format = new AudioFormat.Builder().setEncoding(encoding).setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).build()
          if (AudioTrack.isDirectPlaybackSupported(format, attributes)) names else Nil
        }
        catch { case _: Throwable => Nil }
      }.toSet
    }

  /** Whether it can play `codec`. Only a codec it is sure it cannot play is a no: an unnamed one, or a device that
    * would not list what it has, is a yes, since the cost of a wrong no is a transcode nobody needed. */
  def plays(codec: String): Boolean = {
    val name = codec.trim.toLowerCase
    name.isEmpty || playable.isEmpty || playable.contains(name)
  }
}
