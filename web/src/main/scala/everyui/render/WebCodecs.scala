package everyui.render

import everyui.component.MediaFormat
import org.scalajs.dom

import scala.scalajs.js

/** What this browser, or this TV's engine, plays (`Platform.decodes`), each question asked of it as a MIME type with
  * its codecs: Media Source's `isTypeSupported` (what a stream plays through) or a media element's `canPlayType` (what
  * the element plays itself, which on a TV is the TV's own pipeline), either one's yes a yes.
  *
  *  - A codec by a media server's name ("hevc", "eac3") with its container, profile, level and bit depth is written
  *    out as the codec string that says them (`hvc1.2.4.L153.B0` for HEVC Main 10 at 5.1); a codec string as RFC 6381
  *    writes it (`hvc1.2.4.L153.B0`, `mp4a.40.2`, `ec-3`, `dvh1.08.06`) is asked as it is; a whole MIME type
  *    (`video/webm; codecs="vp9"`) as it is.
  *  - The container sets the MIME type: MP4 (and MOV, M4V) as `video/mp4`, a TS or HLS stream as MP4 too (hls.js
  *    turns it into that, and a TV's player takes it), WebM as `video/webm`, Matroska as `video/x-matroska` (which few
  *    engines say they play), any other as `video/<it>`. A sound is asked in `audio/mp4`, but Opus and Vorbis in
  *    `audio/webm` unless the container is MP4, MP3 alone as `audio/mpeg`, FLAC in a `.flac` as `audio/flac`, PCM as
  *    WAV.
  *  - HDR ("hdr10", "hlg", "hdr10plus", "dolbyvision", alone or in a codec string's transfer characteristics) is
  *    what the host says it shows (`AppHost.display`: an LG TV's own flags, `WebOSDisplay`), else the screen's
  *    `(dynamic-range: high)` where the engine knows the query, else not known: a no for a name asked alone (the
  *    screen vouching for HDR10 and HLG only), the codec's answer in a codec string; Dolby Vision with no word from the
  *    host is the engine's answer for its codec string (`dvh1.05.06` or `dvhe.05.06` where none is given). "atmos"
  *    is the host's flag, else E-AC-3's answer (Atmos's bed plays wherever E-AC-3 does).
  *
  * A name not known here is a yes, since the cost of a wrong no is a transcode nobody needed; codecs no browser plays
  * (VC-1, MPEG-2, WMV and WMA) are a no. A string the engine refuses is asked in its family's other forms before it is a
  * no (`CodecMime.forms`: `vp9` for `vp09.00.*.08`, `dvhe` for `dvh1`). The engine's answers are kept; HDR's are
  * asked each time, as a TV says them a moment after the app starts. */
object WebCodecs {
  private val never: Set[String] = Set("vc1", "mpeg2video", "mpeg1video", "msmpeg4", "wmv3", "wmv2", "wmav2", "wma")

  private var asked: Map[String, Boolean] = Map.empty

  /** Whether it plays `codec`: a name, a codec string, a whole MIME type, an HDR name or "atmos". */
  def plays(codec: String): Boolean = {
    val c = codec.trim
    if (c.isEmpty) true
    else if (c.contains("/")) engine(c)
    else if (c.toLowerCase == "atmos") host("atmos").getOrElse(plays(MediaFormat.of("eac3", "", "", "", 0)))
    else plays(MediaFormat.of(c, "", "", "", 0))
  }

  /** Whether it plays `f`: its HDR shown, and its codec in its container (asked in its family's other forms where an
    * older engine refuses the one written: `CodecMime.forms`). An HDR name alone is what the host or the screen says,
    * and no where neither says (Dolby Vision: the engine's word for its codec string); the screen's dynamic range
    * vouches for HDR10 and HLG, never for HDR10+. */
  def plays(f: MediaFormat): Boolean =
    if (f.codec.isEmpty) {
      val said = if (f.hdr.isEmpty) Some(true)
        else host(f.hdr).orElse(if (f.hdr == "hdr10" || f.hdr == "hlg") dynamicRange else None)
      said.getOrElse(f.hdr == "dolbyvision" && (engine("video/mp4; codecs=\"dvh1.05.06\"") ||
        engine("video/mp4; codecs=\"dvhe.05.06\"")))
    }
    else if (never.contains(f.codec)) false
    else hdr(f.hdr).getOrElse(true) &&
      CodecMime.of(f, host("dolbyvision").nonEmpty).forall(asked => CodecMime.forms(asked).exists(engine))

  /** What the host or the screen says of `kind` of HDR, None where neither says; "" is no HDR, shown everywhere. */
  def hdr(kind: String): Option[Boolean] =
    if (kind.isEmpty) Some(true)
    else host(kind).orElse(if (kind == "dolbyvision") None else dynamicRange)

  private def host(feature: String): Option[Boolean] = AppHosts.current.flatMap(_.display(feature))

  // the screen's dynamic range, where the engine knows the query (Chrome 98 and later, Safari): neither range matching
  // is a query it does not know
  private def dynamicRange: Option[Boolean] = {
    val window = dom.window.asInstanceOf[js.Dynamic]
    if (js.typeOf(window.matchMedia) != "function") None
    else try {
      if (window.matchMedia("(dynamic-range: high)").matches.asInstanceOf[Boolean]) Some(true)
      else if (window.matchMedia("(dynamic-range: standard)").matches.asInstanceOf[Boolean]) Some(false)
      else None
    } catch { case _: Throwable => None }
  }

  // the engine's answer for a MIME type with its codecs, kept: Media Source's yes, else the element's
  private def engine(mime: String): Boolean = asked.getOrElse(mime, {
    val source = WebFeatures.global("MediaSource").orNull
    val bySource =
      if (source == null || js.typeOf(source.isTypeSupported) != "function") None
      else try Some(source.isTypeSupported(mime).asInstanceOf[Boolean]) catch { case _: Throwable => None }
    val answer = bySource.filter(identity).getOrElse {
      val tag = if (mime.startsWith("audio")) "audio" else "video"
      val element = dom.document.createElement(tag).asInstanceOf[js.Dynamic]
      try element.canPlayType(mime).asInstanceOf[String].nonEmpty catch { case _: Throwable => bySource.getOrElse(true) }
    }
    asked = asked + (mime -> answer)
    answer
  })
}
