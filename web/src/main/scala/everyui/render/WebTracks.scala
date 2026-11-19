package everyui.render

import everyui.component.{MediaSidecar, MediaTrack}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** The tracks of a media element beside what hls.js lists ([[WebMedia]]): the element's own (a file's, or an HLS
  * stream's that the browser or the TV plays itself), the sound tracks and subtitles an HLS master playlist names where
  * the player plays them without listing them, and the subtitle files given beside the source (`MediaSidecar`).
  *
  *  - **Its own**: `audioTracks` where the engine has them (Safari, a TV's runtime; Chromium keeps them behind a flag),
  *    and `textTracks` of kind `subtitles` or `captions` (an HLS stream's WebVTT renditions and its CEA-608 captions,
  *    format "CC"), metadata left out; each id its place among them.
  *  - **The playlist's**: where the player lists no sound tracks (or no subtitles) of an HLS stream it plays itself, the
  *    master playlist is read once (`HlsRenditions`) and its renditions listed, each id its place in its group, which is
  *    hls.js's index for it too.
  *  - **Sidecars**: each file fetched (with the source's headers; CORS from another origin), SubRip made WebVTT
  *    (`SubRipVtt`), and given to the element as a `<track>` of a `blob:` of the page's own, so neither the file's
  *    origin nor the element's `crossorigin` matters; ids `side<n>`, after the file's own, in the order given, labelled
  *    as Android labels them (the label, else the language, else "Subtitles"). A file a browser cannot show (SubStation,
  *    pictures) is left out, and said. */
final class WebTracks(el: html.Element) {
  private val m = el.asInstanceOf[js.Dynamic]
  private var files: List[MediaSidecar] = Nil
  // the sidecars made: their place in `files`, their `<track>`, its `blob:`
  private var made: List[(Int, html.Element, String)] = Nil
  private var renditions: Map[String, List[HlsRendition]] = Map.empty
  // the sidecar to show once it is made ("" none)
  private var shown = ""
  // each source a new round, so what an older one's fetch answers late is let go
  private var round = 0

  /** The subtitle files to offer beside the source; for a source open now (`headers` its), made at once. */
  def sidecars(list: List[MediaSidecar], open: Boolean, headers: Map[String, String]): Unit = if (list != files) {
    files = list
    unmake()
    if (open) make(headers)
  }

  /** A source opened: its sidecars made, and an HLS stream played by the element itself (`native`) read for its
    * renditions, each listed as it arrives. */
  def opened(url: String, native: Boolean, headers: Map[String, String]): Unit = {
    clear()
    make(headers)
    if (native && WebMedia.hls(url)) {
      val at = round
      fetchText(url, headers, text => if (at == round && HlsRenditions.master(text)) {
        renditions = Map("AUDIO" -> HlsRenditions.of(text, "AUDIO"), "SUBTITLES" -> HlsRenditions.of(text, "SUBTITLES"))
      })
    }
  }

  /** Every track and file of the last source let go: the `<track>`s taken off, their `blob:`s revoked. The sidecar
    * shown stays the choice for the next source, as a text choice does on every platform. */
  def clear(): Unit = {
    round += 1
    renditions = Map.empty
    unmake()
  }

  /** How many sidecar `<track>`s are on the element. */
  def held: Int = made.size

  /** The element's own tracks of `kind`. */
  def own(kind: String): List[MediaTrack] = ownList(kind).zipWithIndex.map { case (t, i) =>
    MediaTrack(i.toString, kind, text(t.language), text(t.label), if (text(t.kind) == "captions") "CC" else "")
  }

  /** The renditions of `kind` the master playlist names, where it was read. */
  def listed(kind: String): List[MediaTrack] =
    renditions.getOrElse(if (kind == MediaTrack.Audio) "AUDIO" else "SUBTITLES", Nil).zipWithIndex.map { case (r, i) =>
      MediaTrack(i.toString, kind, r.language, r.name)
    }

  /** The playlist's default rendition of `kind`, by its place. */
  def listedDefault(kind: String): Int =
    math.max(0, renditions.getOrElse(if (kind == MediaTrack.Audio) "AUDIO" else "SUBTITLES", Nil).indexWhere(_.default))

  /** The sidecars made, in the order given. */
  def side: List[MediaTrack] = made.sortBy(_._1).map { case (i, _, _) =>
    val f = files(i)
    MediaTrack("side" + i, MediaTrack.Text, f.language, label(f), "")
  }

  /** The closed captions among the element's own text tracks (hls.js puts a stream's there), ids `cc<n>`. */
  def captions: List[MediaTrack] = ownList(MediaTrack.Text).filter(t => text(t.kind) == "captions").zipWithIndex
    .map { case (t, i) => MediaTrack("cc" + i, MediaTrack.Text, text(t.language), text(t.label), "CC") }

  /** The captions `cc<n>` shown, the rest hidden (hidden, not disabled, so hls.js goes on filling them). */
  def showCaptions(id: String): Unit = ownList(MediaTrack.Text).filter(t => text(t.kind) == "captions").zipWithIndex
    .foreach { case (t, i) => t.mode = if ("cc" + i == id) "showing" else "hidden" }

  /** The element's own track `id` of `kind` heard or shown, the rest not; "" leaves the sound alone, and shows no
    * text. */
  def selectOwn(kind: String, id: String): Unit = if (kind == MediaTrack.Text || id.nonEmpty) {
    ownList(kind).zipWithIndex.foreach { case (t, i) =>
      val on = id == i.toString
      if (kind == MediaTrack.Audio) t.enabled = on else t.mode = if (on) "showing" else "disabled"
    }
  }

  /** The sidecar `id` shown (`side<n>`), now or once it is made, and the others not; "" shows none. */
  def showSide(id: String): Unit = {
    shown = id
    made.foreach { case (i, track, _) =>
      track.asInstanceOf[js.Dynamic].track.mode = if (id == "side" + i) "showing" else "disabled"
    }
  }

  private def ownList(kind: String): List[js.Dynamic] = {
    val list = m.selectDynamic(if (kind == MediaTrack.Audio) "audioTracks" else "textTracks")
    if (js.isUndefined(list) || list == null) Nil
    else (0 until list.length.asInstanceOf[Int]).toList.map(i => list.selectDynamic(i.toString)).filter { t =>
      kind == MediaTrack.Audio ||
        ((text(t.kind) == "subtitles" || text(t.kind) == "captions") && !text(t.id).startsWith(WebTracks.prefix))
    }
  }

  private def make(headers: Map[String, String]): Unit = {
    val at = round
    files.zipWithIndex.foreach { case (f, i) =>
      fetchText(f.url, headers, body => if (at == round) SubRipVtt(body) match {
        case None => everyui.scribe.warn("media: subtitles " + label(f) + " are in a format a browser cannot show")
        case Some(vtt) =>
          val blob = js.Dynamic.newInstance(js.Dynamic.global.Blob)(js.Array(vtt), js.Dynamic.literal(`type` = "text/vtt"))
          val address = js.Dynamic.global.URL.createObjectURL(blob).asInstanceOf[String]
          val track = dom.document.createElement("track").asInstanceOf[html.Element]
          track.id = WebTracks.prefix + i
          track.setAttribute("kind", "subtitles")
          track.setAttribute("label", label(f))
          if (f.language.nonEmpty) track.setAttribute("srclang", f.language)
          track.setAttribute("src", address)
          el.appendChild(track)
          track.asInstanceOf[js.Dynamic].track.mode = if (shown == "side" + i) "showing" else "disabled"
          made = (i, track, address) :: made
      })
    }
  }

  private def unmake(): Unit = {
    made.foreach { case (_, track, address) =>
      if (track.parentNode != null) track.parentNode.removeChild(track)
      js.Dynamic.global.URL.revokeObjectURL(address)
    }
    made = Nil
  }

  private def label(f: MediaSidecar): String =
    if (f.label.nonEmpty) f.label else if (f.language.nonEmpty) f.language else "Subtitles"

  // the body of `url` as text, to `got`; a failure said and let be
  private def fetchText(url: String, headers: Map[String, String], got: String => Unit): Unit = {
    val init = js.Dynamic.literal(headers = js.Dictionary(headers.toSeq*))
    val answered: js.Function1[js.Dynamic, js.Any] = r =>
      if (r.ok.asInstanceOf[Boolean]) r.text() else js.Promise.reject("HTTP " + r.status.toString)
    val read: js.Function1[js.Any, Unit] = body => got(body.toString)
    val failed: js.Function1[js.Any, Unit] = why =>
      everyui.scribe.warn("media: " + WebMedia.origin(url) + " could not be read for its tracks: " + why.toString)
    WebFetch(url, init).`then`(answered).`then`(read, failed)
  }

  private def text(value: js.Dynamic): String = if (js.isUndefined(value) || value == null) "" else value.toString
}

object WebTracks {
  /** The id each sidecar's `<track>` has, before its place. */
  val prefix: String = "everyui-side-"
}
