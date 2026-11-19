package everyui.app

import everyui.component.{Container, TextView}
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.ui

/** What the device says it decodes (`Platform.decodes`), asked as an app deciding whether to play a file directly
  * would: HEVC at Main and Main 10 (and as 10 bits with no profile named) in MP4, Matroska and TS, at level 5.1; H.264
  * High in each; VP9 and AV1 at 10 bits; the plain `decodes(codec)` of each; the sounds a media server names; the codec
  * strings a playlist names (`hvc1.2.4.L153.B0`, `mp4a.40.2`, `ec-3`, `dvh1.08.06`) by container; and HDR ("hdr10",
  * "hlg", "hdr10plus", "dolbyvision") and "atmos". Each answer is logged ("[ui] decodes: hevc main10 5.1 mkv = false")
  * and shown; on a Roku the console also has each of roDeviceInfo's answers in full, with the closest format where it
  * says no ("[decodes] video {…}: {…}"). */
object DecodesDemo {
  def run(): Unit = {
    // three columns of 24 lines, so all of them fit a TV's 540
    val columns = List(0, 1, 2).map { i =>
      val column = new Container
      column.layout @= Some(VerticalLayout(2.0))
      column.position.left @= 20.0 + i * 310.0
      column.position.top @= 20.0
      ui.children += column
      column
    }
    var said = 0

    def say(line: String): Unit = {
      Platform.log("decodes: " + line)
      val text = new TextView
      text.font.size @= 12.0
      text.content @= line
      columns(math.min(2, said / 24)).children += text
      said += 1
    }

    for (codec <- List("hevc", "h264", "vp9", "av1", "aac", "ac3", "eac3", "dts", "truehd", "flac", "opus"))
      say(codec + " = " + Platform.decodes(codec))
    for (container <- List("mp4", "mkv", "ts")) {
      say("hevc main 5.1 " + container + " = " + Platform.decodes("hevc", container, "main", "5.1", 8))
      say("hevc main10 5.1 " + container + " = " + Platform.decodes("hevc", container, "main 10", "5.1", 10))
      say("hevc 10-bit " + container + " = " + Platform.decodes("hevc", container, "", "", 10))
      say("h264 high 4.1 " + container + " = " + Platform.decodes("h264", container, "high", "4.1", 8))
    }
    say("vp9 10-bit webm = " + Platform.decodes("vp9", "webm", "", "", 10))
    say("av1 10-bit mp4 = " + Platform.decodes("av1", "mp4", "", "", 10))
    say("hevc mkv (server's level 153) = " + Platform.decodes("hevc", "matroska", "Main 10", "153"))
    // codec strings as a playlist writes them, by container
    for ((codec, container) <- List("avc1.640028" -> "mp4", "avc1.42E01E" -> "ts", "hvc1.1.6.L120.90" -> "mp4",
      "hvc1.2.4.L153.B0" -> "mp4", "hev1.2.4.L153.B0" -> "mkv", "vp09.00.10.08" -> "webm",
      "vp09.02.10.10.01.09.16.09.01" -> "webm", "vp8" -> "webm", "av01.0.05M.08" -> "mp4",
      "av01.0.13M.10.0.112.09.16.09.0" -> "mp4", "dvh1.08.06" -> "mp4", "dvhe.05.06" -> "mp4",
      "mp4a.40.2" -> "mp4", "mp4a.40.5" -> "ts", "ac-3" -> "mp4", "ec-3" -> "mp4", "opus" -> "webm", "opus" -> "mp4",
      "flac" -> "mp4", "dtsc" -> "mp4", "mlpa" -> "mp4", "vorbis" -> "webm"))
      say(codec + " " + container + " = " + Platform.decodes(codec, container))
    // a TV says what it shows a moment after the app starts (an LG's configuration service): asked a second in
    Platform.after(1.0, () => for (shown <- List("hdr10", "hlg", "hdr10plus", "dolbyvision", "atmos"))
      say(shown + " = " + Platform.decodes(shown)))
  }
}
