package everyui.render

/** Whether EveryUI draws a video's subtitles itself ([[WebCues]]) rather than leaving them to the browser's own
  * rendering (`::cue`), and the mode each text track is given for it, plain Scala (so the desktop's specs read it as
  * the page does).
  *
  * A TV's player draws its picture on a plane of its own under the page, through a hole the `<video>` punches in it,
  * and the browser's own subtitles are drawn in that element, so not at all: on an LG E7P (webOS 3.5) a sidecar track
  * showing, its cues loaded and the right one active, drew nothing. So on a TV (`AppHosts`: webOS now, Tizen's and
  * Vega's hosts as they come), and on an engine that squashes what lies over a video (`TvEngine.squashesOverVideo`,
  * before Chromium 62), the chosen track is `hidden` (its cues still loaded and still active) and EveryUI draws them
  * over the picture; elsewhere, a desktop's or a phone's browser, it shows them itself. `WebMedia.drawCues` forces
  * either. */
object CueDrawing {
  /** Whether EveryUI draws the cues: as `forced` says, else on a TV's host (`tvHost`) or an engine that `squashes`. */
  def drawn(forced: Option[Boolean], tvHost: Boolean, squashes: Boolean): Boolean = forced.getOrElse(tvHost || squashes)

  /** The mode a text track is given: the chosen one `showing` where the browser draws it, `hidden` where EveryUI does
    * (loaded and active, drawn by neither the browser nor a TV's player); any other `disabled`. */
  def mode(chosen: Boolean, drawn: Boolean): String =
    if (!chosen) "disabled" else if (drawn) "hidden" else "showing"
}
