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
}
