package everyui.render

/** A playlist's WebVTT subtitles as one file (`WebTracks`), for a TV's own player that plays an HLS stream without
  * listing its subtitle renditions: the rendition's media playlist names its segments, each a plain WebVTT file, and
  * their cues are given to the element together as a `<track>`, as a sidecar is. Plain Scala, so the desktop's specs
  * read it as the page does.
  *
  * The cue times are kept as written: a segment's `X-TIMESTAMP-MAP` ties its `LOCAL` time to the stream's first
  * timestamp (`MPEGTS`) as packagers write it (ffmpeg, Shaka Packager, Apple's tools), which is where a player's
  * position starts at 0. */
object HlsVtt {
  /** The segments a media playlist names, in its order, as written (relative to the playlist's own address). */
  def segments(playlist: String): List[String] =
    playlist.split("\n").toList.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#"))

  /** Whether the playlist is whole (`#EXT-X-ENDLIST`): one that is not (live) grows, and is read again by a player. */
  def ended(playlist: String): Boolean = playlist.contains("#EXT-X-ENDLIST")

  /** The segments' cues as one WebVTT file: each segment's header (`WEBVTT`, `X-TIMESTAMP-MAP`) and its notes, styles
    * and regions left out, a cue repeated in the next segment (one that runs over its end) given once. */
  def join(parts: List[String]): String = {
    val cues = parts.flatMap { part =>
      part.replace("\r\n", "\n").replace('\r', '\n').split("\n\n+").toList.map(_.trim).filter(_.contains("-->"))
    }.distinct
    "WEBVTT\n\n" + cues.mkString("\n\n") + (if (cues.isEmpty) "" else "\n")
  }
}
