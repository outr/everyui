package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** What this browser plays (`Platform.decodes`), by the names a media server gives codecs ("vp9", "hevc", "aac"), each
  * asked of the browser as a MIME type with its codecs: Media Source's `isTypeSupported` where there is one (what a
  * stream plays through), else a video element's `canPlayType`. A whole MIME type (`video/webm; codecs="vp9"`) is
  * asked as it is. A name not known here is a yes, since the cost of a wrong no is a transcode nobody needed; one a
  * browser has nothing for (DTS, TrueHD) is a no. */
object WebCodecs {
  private val types: Map[String, String] = Map(
    "h264" -> "video/mp4; codecs=\"avc1.42E01E\"", "avc" -> "video/mp4; codecs=\"avc1.42E01E\"",
    "hevc" -> "video/mp4; codecs=\"hvc1.1.6.L93.B0\"", "h265" -> "video/mp4; codecs=\"hvc1.1.6.L93.B0\"",
    "vp8" -> "video/webm; codecs=\"vp8\"", "vp9" -> "video/webm; codecs=\"vp9\"",
    "av1" -> "video/mp4; codecs=\"av01.0.05M.08\"",
    "aac" -> "audio/mp4; codecs=\"mp4a.40.2\"", "ac3" -> "audio/mp4; codecs=\"ac-3\"",
    "eac3" -> "audio/mp4; codecs=\"ec-3\"", "opus" -> "audio/webm; codecs=\"opus\"",
    "vorbis" -> "audio/ogg; codecs=\"vorbis\"", "flac" -> "audio/flac", "mp3" -> "audio/mpeg")
  private val none: Set[String] = Set("dca", "dts", "dts_hd", "truehd", "mlp", "vc1", "mpeg2video", "mpeg1video")

  private var asked: Map[String, Boolean] = Map.empty

  def plays(codec: String): Boolean = {
    val name = codec.trim.toLowerCase
    if (name.isEmpty) true
    else asked.getOrElse(name, {
      val answer =
        if (name.contains("/")) supported(codec.trim)
        else if (none.contains(name)) false
        else types.get(name).forall(supported)
      asked = asked + (name -> answer)
      answer
    })
  }

  private def supported(mime: String): Boolean = {
    val source = js.Dynamic.global.MediaSource
    val bySource =
      if (js.isUndefined(source) || js.typeOf(source.isTypeSupported) != "function") None
      else try Some(source.isTypeSupported(mime).asInstanceOf[Boolean]) catch { case _: Throwable => None }
    bySource.filter(identity).getOrElse {
      val tag = if (mime.startsWith("audio")) "audio" else "video"
      val video = dom.document.createElement(tag).asInstanceOf[js.Dynamic]
      try video.canPlayType(mime).asInstanceOf[String].nonEmpty catch { case _: Throwable => bySource.getOrElse(true) }
    }
  }

  /** Whether the browser plays `f`: a picture's codec asked as the MIME type that says its container, profile, level
    * and bit depth (`video/mp4; codecs="hvc1.2.4.L153.B0"` for HEVC Main 10 at 5.1); a TS or HLS stream as MP4, which
    * hls.js turns it into, Matroska as `video/x-matroska`, which few browsers say they play; a sound's codec as
    * [[plays]] has it. */
  def plays(f: everyui.component.MediaFormat): Boolean =
    mime(f).fold(plays(f.codec))(m => asked.getOrElse(m, {
      val answer = !none.contains(f.codec) && supported(m)
      asked = asked + (m -> answer)
      answer
    }))

  /** The MIME type with its codecs that asks after `f`, or None for a codec not written out here. */
  def mime(f: everyui.component.MediaFormat): Option[String] = {
    val base = f.container match {
      case "webm" => "video/webm"
      case "mkv" => "video/x-matroska"
      case _ => "video/mp4"
    }
    def two(n: Int): String = if (n < 10) "0" + n else n.toString
    val depth = if (f.deep) 10 else 8
    val codecs = f.codec match {
      case "hevc" =>
        val level = if (f.level > 0.0) math.round(f.level * 30.0).toInt else 93
        Some((if (f.profile == "main10" || f.deep) "hvc1.2.4.L" else "hvc1.1.6.L") + level + ".B0")
      case "h264" =>
        val profile =
          if (f.profile == "high10" || f.deep) "6E00" else if (f.profile == "high") "6400"
          else if (f.profile == "main") "4D40" else "42E0"
        val level = if (f.level > 0.0) math.round(f.level * 10.0).toInt else 30
        Some("avc1." + profile + (if (level < 16) "0" else "") + Integer.toHexString(level).toUpperCase)
      case "vp9" =>
        val level = if (f.level > 0.0) math.round(f.level * 10.0).toInt else 10
        Some("vp09." + (if (f.profile == "2" || f.deep) "02" else "00") + "." + two(level) + "." + two(depth))
      case "av1" =>
        // seq_level_idx: (major - 2) * 4 + minor, 5 (3.1) where none is said
        val level = if (f.level >= 2.0) (f.level.toInt - 2) * 4 + math.round((f.level - f.level.toInt) * 10.0).toInt
          else 5
        Some("av01.0." + two(level) + "M." + two(depth))
      case _ => None
    }
    codecs.map(c => base + "; codecs=\"" + c + "\"")
  }
}
