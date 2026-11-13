package everyui.render

import android.media.{AudioAttributes, AudioFormat, AudioTrack, MediaCodecInfo, MediaCodecList}
import everyui.component.MediaFormat

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

  /** Whether it can play `f`: the codec (as [[plays]]), and, where a profile, a level or ten bits is asked of a
    * picture's codec, a decoder for it that lists the profile at that level or above (HEVC's Main or Main 10, H.264's
    * Baseline, Main, High or High 10, VP9's 0 or 2, AV1's Main at 8 or 10 bits; levels for HEVC and H.264). Media3
    * opens every container a server sends (MP4, Matroska, WebM, TS, HLS, DASH), so the container rules nothing out. A
    * decoder that lists no profiles, or a profile not known here, is a yes. */
  def plays(f: MediaFormat): Boolean =
    if (!plays(f.codec)) false
    else if (!f.video || (f.profile.isEmpty && f.level <= 0.0 && !f.deep)) true
    else mimes.get(f.codec).forall { mime =>
      val wanted = profileOf(f)
      val level = levelOf(f)
      if (wanted.isEmpty && level == 0) true
      else try {
        val lists = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos.toList
          .filter(i => !i.isEncoder && i.getSupportedTypes.exists(_.equalsIgnoreCase(mime)))
          .map(i => i.getCapabilitiesForType(mime).profileLevels.toList)
        lists.isEmpty || lists.exists(pls => pls.isEmpty || pls.exists(pl =>
          wanted.forall(_ == pl.profile) && pl.level >= level))
      }
      catch {
        case t: Throwable =>
          Platform.logError("codecs: the device would not list " + mime + "'s profiles", t)
          true
      }
    }

  private def profileOf(f: MediaFormat): Option[Int] = {
    import MediaCodecInfo.CodecProfileLevel as P
    val p = f.profile
    f.codec match {
      case "hevc" =>
        if (p == "main10" || (p.isEmpty && f.deep)) Some(P.HEVCProfileMain10)
        else if (p == "main") Some(P.HEVCProfileMain)
        else None
      case "h264" =>
        if (p == "high10" || (p.isEmpty && f.deep)) Some(P.AVCProfileHigh10)
        else if (p == "high") Some(P.AVCProfileHigh)
        else if (p == "main") Some(P.AVCProfileMain)
        else if (p == "baseline" || p == "constrainedbaseline") Some(P.AVCProfileBaseline)
        else None
      case "vp9" =>
        if (p == "2" || (p.isEmpty && f.deep)) Some(P.VP9Profile2)
        else if (p == "0") Some(P.VP9Profile0)
        else None
      case "av1" =>
        if (f.deep) Some(P.AV1ProfileMain10) else if (p == "main") Some(P.AV1ProfileMain8) else None
      case _ => None
    }
  }

  /** The level as MediaCodecInfo's constant for it (each a bit, higher for a higher level), 0 for any. */
  private def levelOf(f: MediaFormat): Int =
    if (f.level <= 0.0) 0
    else {
      val (levels, step) =
        if (f.codec == "hevc") (List(1.0, 2.0, 2.1, 3.0, 3.1, 4.0, 4.1, 5.0, 5.1, 5.2, 6.0, 6.1, 6.2), 2)
        else if (f.codec == "h264")
          (List(1.0, 1.0, 1.1, 1.2, 1.3, 2.0, 2.1, 2.2, 3.0, 3.1, 3.2, 4.0, 4.1, 4.2, 5.0, 5.1, 5.2, 6.0, 6.1, 6.2), 1)
        else (Nil, 1)
      val i = levels.indexWhere(_ >= f.level - 0.001)
      if (levels.isEmpty || i < 0) 0 else 1 << (i * step)
    }
}
