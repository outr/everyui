package everyui.render

import everyui.component.MediaFormat

/** The MIME type with its codecs that a browser is asked after a [[MediaFormat]] by (`WebCodecs`), plain Scala, so the
  * desktop's specs read it as the browser is asked: the codec string as given (`tag`), else as its name, profile,
  * level and bit depth write one, in the MIME type its container and kind call for. */
object CodecMime {
  /** The MIME type for `f`, or None for a codec not written out here. `vouched`: Dolby Vision the host has said it
    * shows, asked as the codec under it. */
  def of(f: MediaFormat, vouched: Boolean = false): Option[String] = {
    val codecs = if (f.tag.nonEmpty && !(vouched && f.hdr == "dolbyvision")) Some(f.tag) else written(f)
    if (f.codec == "mp3" && (f.container.isEmpty || f.container == "mp3") && f.tag.isEmpty) Some("audio/mpeg")
    else if (f.codec == "flac" && (f.container.isEmpty || f.container == "flac") && f.tag.isEmpty) Some("audio/flac")
    else if (f.codec == "pcm" || f.codec == "lpcm" || f.codec.startsWith("pcm_")) Some("audio/wav; codecs=\"1\"")
    else codecs.map(c => base(f) + "; codecs=\"" + c + "\"")
  }
  private def base(f: MediaFormat): String =
    if (f.video) f.container match {
      case "" | "mp4" | "mov" | "m4v" | "ts" | "hls" | "fmp4" | "cmaf" => "video/mp4"
      case "webm" => "video/webm"
      case "mkv" => "video/x-matroska"
      case "ogg" | "ogv" => "video/ogg"
      case other => "video/" + other
    }
    else if (f.codec == "opus" || f.codec == "vorbis") f.container match {
      case "mp4" | "mov" | "m4a" | "ts" | "hls" | "fmp4" | "cmaf" => "audio/mp4"
      case "ogg" | "oga" => "audio/ogg"
      case _ => "audio/webm"
    }
    else "audio/mp4"

  // the codec string a name, a profile, a level and a bit depth come to
  private def written(f: MediaFormat): Option[String] = {
    def two(n: Int): String = if (n < 10) "0" + n else n.toString
    val depth = if (f.deep) 10 else 8
    f.codec match {
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
      case "vp8" => Some("vp8")
      case "aac" => Some(if (f.profile == "he") "mp4a.40.5" else if (f.profile == "hev2") "mp4a.40.29" else "mp4a.40.2")
      case "mp3" => Some("mp4a.6B")
      case "ac3" => Some("ac-3")
      case "eac3" => Some("ec-3")
      case "opus" => Some("opus")
      case "vorbis" => Some("vorbis")
      case "flac" => Some("flac")
      case "alac" => Some("alac")
      case "dts" => Some(if (f.profile == "hd") "dtsh" else if (f.profile == "x") "dtsx"
        else if (f.profile == "express") "dtse" else "dtsc")
      case "truehd" => Some("mlpa")
      case _ => None
    }
  }
}
