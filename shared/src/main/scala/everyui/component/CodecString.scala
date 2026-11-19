package everyui.component

/** A codec as RFC 6381 writes it (an HLS playlist's CODECS, a DASH manifest's, `MediaSource.isTypeSupported`'s), read
  * into a [[MediaFormat]]: the codec in the one spelling `Platform.decodes` asks after, its profile, level and bit depth
  * where the string says them, its HDR where it says that (a VP9 or AV1 string's transfer characteristics, a Dolby
  * Vision sample entry), and the string itself (`tag`), for a browser to be asked after exactly.
  *
  *  - `avc1.640028` (or `avc3`): H.264, its profile_idc in hex (42 Baseline, 4D Main, 58 Extended, 64 High, 6E High 10,
  *    7A High 4:2:2, F4 High 4:4:4) and its level_idc (28 is 4.0); the old decimal form `avc1.66.30` as well;
  *  - `hvc1.2.4.L153.B0` (or `hev1`): HEVC, its profile (1 Main, 2 Main 10, 3 Main Still Picture, 4 Range Extensions)
  *    and its tier and level (L for Main tier, H for High, the level times 30);
  *  - `vp09.02.51.10.01.09.16.09.00`: VP9, profile, level, bit depth, and transfer characteristics 16 (PQ, HDR10) or 18
  *    (HLG); `vp8`, `vp08`;
  *  - `av01.0.13M.10.0.112.09.16.09.0`: AV1, profile (0 Main, 1 High, 2 Professional), seq_level_idx (13 is 5.1) and
  *    tier, bit depth, and transfer characteristics as VP9's;
  *  - `dvh1.08.06`, `dvhe.05.06`: Dolby Vision over HEVC (Main 10); `dav1.10.06` over AV1; `dva1`, `dvav` over H.264;
  *  - `mp4a.40.2` (AAC LC; .5 HE-AAC, .29 HE-AAC v2, .34 MP3), `mp4a.69` and `mp4a.6B` (MP3), `mp4a.A5` (AC-3),
  *    `mp4a.A6` (E-AC-3); `ac-3`, `ec-3`, `ec+3` (E-AC-3 with Atmos, its profile "joc"); `opus`, `flac` (`fLaC`),
  *    `vorbis`, `alac`; `dtsc` (DTS), `dtse` (DTS Express), `dtsh` and `dtsl` (DTS-HD, profile "hd"), `dtsx` (DTS:X,
  *    profile "x"); `mlpa` (TrueHD).
  *
  * Anything else is None: a plain name ("hevc", "aac") is MediaFormat's to read. */
object CodecString {
  def read(raw: String): Option[MediaFormat] = {
    val tag = raw.trim
    val lower = tag.toLowerCase
    val parts = lower.split('.').toList
    val head = parts.headOption.getOrElse("")
    def at(i: Int): String = if (i < parts.length) parts(i) else ""
    def made(codec: String, profile: String, level: Double, depth: Int, hdr: String): Option[MediaFormat] =
      Some(MediaFormat(codec, "", profile, level, depth, hdr, tag))
    if (head == "avc1" || head == "avc3") {
      if (parts.length == 3) made("h264", avcProfile(number(at(1))), number(at(2)) / 10.0, 0, "")
      else {
        val p = at(1)
        val idc = if (p.length >= 2) hex(p.substring(0, 2)) else -1
        val constraints = if (p.length >= 4) hex(p.substring(2, 4)) else 0
        val level = if (p.length >= 6) hex(p.substring(4, 6)) else -1
        val profile = if (idc == 66 && (constraints & 64) != 0) "constrainedbaseline" else avcProfile(idc)
        made("h264", profile, if (level > 0) level / 10.0 else 0.0, if (idc == 110) 10 else 0, "")
      }
    }
    else if (head == "hvc1" || head == "hev1") {
      // the profile may lead with its space (A, B or C)
      val p = at(1).dropWhile(c => c == 'a' || c == 'b' || c == 'c')
      val idc = number(p).toInt
      val profile = if (idc == 1) "main" else if (idc == 2) "main10" else if (idc == 3) "mainstillpicture"
        else if (idc == 4) "rext" else ""
      val tierLevel = at(3)
      val level = if (tierLevel.length > 1 && (tierLevel.charAt(0) == 'l' || tierLevel.charAt(0) == 'h'))
        number(tierLevel.substring(1)) / 30.0 else 0.0
      made("hevc", profile, level, if (idc == 2) 10 else 0, "")
    }
    else if (head == "vp09") {
      val depth = number(at(3)).toInt
      made("vp9", if (at(1).nonEmpty) number(at(1)).toInt.toString else "", number(at(2)) / 10.0,
        if (depth > 0) depth else 0, transfer(at(6)))
    }
    else if (head == "vp8" || head == "vp08") made("vp8", "", 0.0, 0, "")
    else if (head == "av01") {
      val idc = number(at(1)).toInt
      val profile = if (at(1).isEmpty) "" else if (idc == 0) "main" else if (idc == 1) "high" else "professional"
      val seq = at(2).takeWhile(c => c >= '0' && c <= '9')
      val level = if (seq.isEmpty) 0.0 else {
        val i = number(seq).toInt
        (2 + i / 4).toDouble + (i % 4).toDouble / 10.0
      }
      val depth = number(at(3)).toInt
      made("av1", profile, level, if (depth > 0) depth else 0, transfer(at(7)))
    }
    else if (head == "dvh1" || head == "dvhe") made("hevc", "main10", 0.0, 10, "dolbyvision")
    else if (head == "dav1") made("av1", "main", 0.0, 10, "dolbyvision")
    else if (head == "dva1" || head == "dvav") made("h264", "", 0.0, 0, "dolbyvision")
    else if (head == "mp4a") {
      val oti = at(1)
      val kind = at(2)
      if (oti == "40") {
        if (kind == "34") made("mp3", "", 0.0, 0, "")
        else made("aac", if (kind == "5") "he" else if (kind == "29") "hev2" else if (kind == "2") "lc" else "",
          0.0, 0, "")
      }
      else if (oti == "69" || oti == "6b") made("mp3", "", 0.0, 0, "")
      else if (oti == "a5") made("ac3", "", 0.0, 0, "")
      else if (oti == "a6") made("eac3", "", 0.0, 0, "")
      else made("aac", "", 0.0, 0, "")
    }
    else if (lower == "ac-3") made("ac3", "", 0.0, 0, "")
    else if (lower == "ec-3") made("eac3", "", 0.0, 0, "")
    else if (lower == "ec+3") made("eac3", "joc", 0.0, 0, "")
    else if (lower == "dtsc") made("dts", "", 0.0, 0, "")
    else if (lower == "dtse") made("dts", "express", 0.0, 0, "")
    else if (lower == "dtsh" || lower == "dtsl") made("dts", "hd", 0.0, 0, "")
    else if (lower == "dtsx") made("dts", "x", 0.0, 0, "")
    else if (lower == "mlpa") made("truehd", "", 0.0, 0, "")
    else if (lower == "flac" && tag == "fLaC") made("flac", "", 0.0, 0, "")
    else if (lower == "opus" && tag == "Opus") made("opus", "", 0.0, 0, "")
    else None
  }

  /** H.264's profile by its profile_idc. */
  private def avcProfile(idc: Double): String = {
    val i = idc.toInt
    if (i == 66) "baseline" else if (i == 77) "main" else if (i == 88) "extended" else if (i == 100) "high"
    else if (i == 110) "high10" else if (i == 122) "high422" else if (i == 244) "high444" else ""
  }

  /** The HDR a transfer characteristic says: 16 is PQ (SMPTE ST 2084, HDR10), 18 is HLG. */
  private def transfer(tc: String): String = {
    val t = number(tc).toInt
    if (t == 16) "hdr10" else if (t == 18) "hlg" else ""
  }

  // digits as a number, 0 for none; the letters after them let be
  private def number(s: String): Double = {
    var n = 0.0
    var i = 0
    while (i < s.length && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
      n = n * 10.0 + (s.charAt(i) - '0').toDouble
      i += 1
    }
    n
  }

  // two hex digits as a number, -1 where they are not
  private def hex(s: String): Int = {
    val digits = "0123456789abcdef"
    var n = 0
    var i = 0
    var ok = s.nonEmpty
    while (i < s.length) {
      val d = digits.indexOf(s.charAt(i).toString)
      if (d < 0) ok = false else n = n * 16 + d
      i += 1
    }
    if (ok) n else -1
  }
}
