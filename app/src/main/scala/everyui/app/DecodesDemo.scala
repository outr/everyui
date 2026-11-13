package everyui.app

import everyui.component.{Container, TextView}
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.ui

/** What the device says it decodes (`Platform.decodes`), asked as an app deciding whether to play a file directly
  * would: HEVC at Main and Main 10 (and as 10 bits with no profile named) in MP4, Matroska and TS, at level 5.1; H.264
  * High in each; VP9 and AV1 at 10 bits; the plain `decodes(codec)` of each; and the sounds a media server names. Each
  * answer is logged ("[ui] decodes: hevc main10 5.1 mkv = false") and shown; on a Roku the console also has each of
  * roDeviceInfo's answers in full, with the closest format where it says no ("[decodes] video {…}: {…}"). */
object DecodesDemo {
  def run(): Unit = {
    val column = new Container
    column.layout @= Some(VerticalLayout(4.0))
    column.position.left @= 40.0
    column.position.top @= 30.0
    ui.children += column

    def say(line: String): Unit = {
      Platform.log("decodes: " + line)
      val text = new TextView
      text.font.size @= 18.0
      text.content @= line
      column.children += text
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
  }
}
