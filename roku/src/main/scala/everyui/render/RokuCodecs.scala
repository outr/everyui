package everyui.render

import everyui.component.MediaFormat
import everyui.roku.*

/** What a Roku plays (`Platform.decodes`), asked of the device: roDeviceInfo's CanDecodeVideo for a picture's codec
  * and CanDecodeAudio for a sound's, with the codec in the device's own names ("mpeg4 avc" for H.264, "hevc", "vp9",
  * "av1"; "dts" for DCA, "lpcm" for PCM), and, where the app says them, the profile in its names ("main 10" for HEVC
  * at ten bits, "high", "profile 2" for VP9 at ten bits), the level ("5.1") and the container ("mkv", "mp4", "ts",
  * "hls"). Roku takes no bit depth: ten bits is asked as the codec's ten-bit profile where none is given. A name not
  * known here is a yes, as everywhere: the cost of a wrong no is a transcode nobody needed. The device's whole answer
  * (its closest format where it says no) is kept for the console (`last`). */
object RokuCodecs {
  private val videoNames: Map[String, String] = Map("hevc" -> "hevc", "h264" -> "mpeg4 avc", "vp9" -> "vp9",
    "av1" -> "av1", "mpeg2video" -> "mpeg2", "mpeg1video" -> "mpeg1")
  private val audioNames: Map[String, String] = Map("aac" -> "aac", "ac3" -> "ac3", "eac3" -> "eac3", "mp3" -> "mp3",
    "flac" -> "flac", "alac" -> "alac", "dca" -> "dts", "dts" -> "dts", "opus" -> "opus", "vorbis" -> "vorbis",
    "pcm" -> "lpcm", "pcm_s16le" -> "lpcm", "lpcm" -> "lpcm", "wmav2" -> "wma", "wma" -> "wma", "truehd" -> "truehd")

  def plays(f: MediaFormat): Boolean = {
    val video = videoNames.get(f.codec)
    val audio = audioNames.get(f.codec)
    if (f.codec.isEmpty) true
    else if (video.nonEmpty) uiCanDecode("video", video.get, profile(f), f.levelText, f.container)
    else if (audio.nonEmpty) uiCanDecode("audio", audio.get, "", "", f.container)
    else true
  }

  /** The device's answer to the last question, as JSON. */
  def last(): String = uiDecodeAnswer()

  /** The profile in the device's names, the ten-bit one where only the bit depth says it. */
  def profile(f: MediaFormat): String = {
    val p = f.profile
    if (f.codec == "hevc") {
      if (p == "main10" || (p.isEmpty && f.deep)) "main 10" else p
    }
    else if (f.codec == "h264") {
      if (p == "high10" || (p.isEmpty && f.deep)) "high 10"
      else if (p == "constrainedbaseline") "baseline"
      else p
    }
    else if (f.codec == "vp9") {
      if (p.isEmpty && f.deep) "profile 2" else if (p.nonEmpty) "profile " + p else ""
    }
    else p
  }
}
