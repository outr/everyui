package everyui.component

/** A codec as `Platform.decodes(codec, container, profile, level, bitDepth)` asks after it, in one spelling whatever
  * the media server's: `codec` "hevc" (for "h265", "hvc1", "hev1"), "h264" (for "avc", "avc1", "mpeg4 avc"), "vp9",
  * "av1" or the name as given; `container` "mkv" (for "matroska"), "ts" (for "mpegts", "m2ts"), "hls" (for "m3u8"),
  * "mp4" (for "m4v") or as given, "" for any; `profile` lower case with nothing but letters and digits ("main10" for
  * "Main 10", "high" for "High", "2" for VP9's "Profile 2"), "" for any; `level` as a number ("5.1" for "5.1", "51",
  * and an HEVC "153", which is the level times 30, as ffprobe and Plex give it), 0 for any; and `bitDepth` 8, 10 or 12,
  * as given or as the profile says (Main 10, High 10, VP9's 2 and 3), 0 for any.
  *
  * `codec` may also be a codec string as RFC 6381 writes it (`hvc1.2.4.L153.B0`, `avc1.640028`, `mp4a.40.2`, `ec-3`,
  * `dvh1.08.06`: [[CodecString]]), read into the same fields, a profile, level or bit depth given beside it winning;
  * the string is kept as `tag` for a browser to be asked after exactly. `hdr` is the HDR it carries: "hdr10" (PQ),
  * "hdr10plus", "hlg" or "dolbyvision", from the string or from a name asked alone (`decodes("hdr10")`, "hlg",
  * "dolbyvision", "dovi"), whose codec is then "" (any); "" for none said. */
case class MediaFormat(codec: String, container: String, profile: String, level: Double, bitDepth: Int,
                       hdr: String = "", tag: String = "") {
  /** A picture's codec, rather than a sound's. */
  def video: Boolean = MediaFormat.videoCodecs.contains(codec)
  /** Ten bits or more. */
  def deep: Boolean = bitDepth >= 10
  /** The level as a device's tables write it: "5.1", "4.0", "" for any. */
  def levelText: String =
    if (level <= 0.0) ""
    else {
      val tenths = math.round(level * 10.0).toInt
      (tenths / 10).toString + "." + (tenths % 10).toString
    }
}

object MediaFormat {
  val videoCodecs: Set[String] = Set("hevc", "h264", "vp9", "vp8", "av1", "mpeg2video", "mpeg1video", "mpeg4",
    "vc1", "msmpeg4")

  /** The HDR names asked alone, by what they are called. */
  val hdrNames: Map[String, String] = Map("hdr10" -> "hdr10", "hdr" -> "hdr10", "pq" -> "hdr10", "smpte2084" -> "hdr10",
    "hdr10+" -> "hdr10plus", "hdr10plus" -> "hdr10plus", "hlg" -> "hlg", "arib-std-b67" -> "hlg",
    "dolbyvision" -> "dolbyvision", "dolby vision" -> "dolbyvision", "dovi" -> "dolbyvision", "dv" -> "dolbyvision")

  def of(codec: String, container: String, profile: String, level: String, bitDepth: Int): MediaFormat = {
    val named = codec.trim.toLowerCase
    CodecString.read(codec) match {
      case Some(read) =>
        val p = compact(profile).replace("profile", "")
        val stated = levelOf(read.codec, level)
        val depth = if (bitDepth > 0) bitDepth else if (p.nonEmpty) depthOf(read.codec, p) else read.bitDepth
        read.copy(container = containerName(container), profile = if (p.nonEmpty) p else read.profile,
          level = if (stated > 0.0) stated else read.level, bitDepth = depth)
      case None if hdrNames.contains(named) =>
        MediaFormat("", containerName(container), "", 0.0, bitDepth, hdrNames(named), "")
      case None =>
        val c = codecName(codec)
        val p = compact(profile).replace("profile", "")
        MediaFormat(c, containerName(container), p, levelOf(c, level), if (bitDepth > 0) bitDepth else depthOf(c, p))
    }
  }

  // ten bits where the profile says it: Main 10, High 10, VP9's and AV1's 2 and 3
  private def depthOf(codec: String, p: String): Int =
    if (p.endsWith("10") || ((codec == "vp9" || codec == "av1") && (p == "2" || p == "3"))) 10 else 0

  def codecName(codec: String): String = {
    val c = codec.trim.toLowerCase
    if (c == "h265" || c == "hvc1" || c == "hev1" || c == "x265") "hevc"
    else if (c == "avc" || c == "avc1" || c == "mpeg4 avc" || c == "x264") "h264"
    else if (c == "vp09") "vp9"
    else if (c == "av01") "av1"
    else if (c == "ac-3" || c == "a52") "ac3"
    else if (c == "ec-3" || c == "e-ac-3" || c == "e-ac3") "eac3"
    else if (c == "dca") "dts"
    else if (c == "mlp") "truehd"
    else c
  }

  def containerName(container: String): String = {
    val c = container.trim.toLowerCase.stripPrefix(".")
    if (c == "matroska") "mkv"
    else if (c == "mpegts" || c == "m2ts" || c == "mts") "ts"
    else if (c == "m3u8") "hls"
    else if (c == "m4v") "mp4"
    else c
  }

  private def compact(s: String): String =
    s.trim.toLowerCase.replace(" ", "").replace("-", "").replace("_", "").replace(".", "")

  /** "5.1" as 5.1, to its tenths (a level has no finer part), and 0 for anything else. */
  private def number(s: String): Double = {
    val dot = s.indexOf(".")
    val whole = (if (dot < 0) s else s.substring(0, dot)).toIntOption.getOrElse(-1)
    val tenth = if (dot < 0 || dot + 1 >= s.length) 0 else s.substring(dot + 1, dot + 2).toIntOption.getOrElse(0)
    if (whole < 0) 0.0 else whole.toDouble + tenth.toDouble / 10.0
  }

  private def levelOf(codec: String, level: String): Double = {
    val raw = number(level.trim.toLowerCase.stripPrefix("l"))
    if (raw <= 0.0) 0.0
    else if (raw < 10.0) raw
    else if (codec == "hevc" && raw >= 30.0) raw / 30.0
    else raw / 10.0
  }
}
