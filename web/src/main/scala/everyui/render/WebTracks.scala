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
  *    hls.js's index for it too. A subtitle rendition chosen is read here, its media playlist and then its segments,
  *    each a plain WebVTT file (`HlsVtt`), and given to the element as a `<track>` as a sidecar is, so the TV's own
  *    player plays on as it was (its pipeline, its HDR, its place).
  *  - **Sidecars**: each file fetched (with the source's headers; CORS from another origin), SubRip made WebVTT
  *    (`SubRipVtt`), and given to the element as a `<track>` of a `blob:` of the page's own, so neither the file's
  *    origin nor the element's `crossorigin` matters; ids `side<n>`, after the file's own, in the order given, labelled
  *    as Android labels them (the label, else the language, else "Subtitles"). A file a browser cannot show (SubStation,
  *    pictures) is left out, and said.
  *
  * What is shown is EveryUI's choice, whatever the engine chooses of itself: a TV's runtime shows a subtitle track as
  * its own caption setting says (webOS 3 showed a sidecar nobody chose, and once two at once), so the modes are set
  * again as a track is added or loaded, as the list says a mode changed, and as the element loads (`enforce`, which
  * [[WebMedia]] calls as it hears the element). Shown is `showing`, or `hidden` where EveryUI draws the cues itself
  * ([[CueDrawing]], [[WebCues]]). */
final class WebTracks(el: html.Element, drawn: () => Boolean = () => false, changed: () => Unit = () => ()) {
  private val m = el.asInstanceOf[js.Dynamic]
  private var files: List[MediaSidecar] = Nil
  // the `<track>`s made, by their id ("side<n>" a sidecar, "hls<n>" a playlist's subtitles), each with its `blob:`
  private var made: List[(String, html.Element, String)] = Nil
  private var renditions: Map[String, List[HlsRendition]] = Map.empty
  // the master playlist's address, where it was read
  private var playlist = ""
  // the made track to show, once it is made ("" none)
  private var shown = ""
  // the element's own text track chosen ("" none), once one is
  private var ownText: Option[String] = None
  // the stream's closed captions chosen ("cc<n>", "" none)
  private var caption = ""
  // the element's own sound track to hear once it lists it again (`restoreOwn`), -1 none
  private var ownAudio = -1
  // each source a new round, so what an older one's fetch answers late is let go
  private var round = 0

  {
    val heard: js.Function1[js.Any, Unit] = _ => enforce()
    val list = m.textTracks
    if (!js.isUndefined(list) && list != null && js.typeOf(list.addEventListener) == "function") {
      list.addEventListener("addtrack", heard)
      list.addEventListener("change", heard)
    }
  }

  /** The subtitle files to offer beside the source; for a source open now (`headers` its), made at once. */
  def sidecars(list: List[MediaSidecar], open: Boolean, headers: Map[String, String]): Unit = if (list != files) {
    files = list
    unmake(_.startsWith("side"))
    if (open) make(headers)
  }

  /** A source opened: its sidecars made, and an HLS stream played by the element itself (`native`) read for its
    * renditions, each listed as it arrives. */
  def opened(url: String, native: Boolean, headers: Map[String, String]): Unit = {
    clear()
    make(headers)
    if (native && WebMedia.hls(url)) {
      val at = round
      playlist = url
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
    playlist = ""
    ownText = None
    ownAudio = -1
    caption = ""
    if (shown.startsWith("hls")) shown = ""
    unmake(_ => true)
  }

  /** How many `<track>`s of EveryUI's (sidecars, a playlist's subtitles) are on the element. */
  def held: Int = made.size

  /** The element's own tracks of `kind`. */
  def own(kind: String): List[MediaTrack] = ownList(kind).zipWithIndex.map { case (t, i) =>
    MediaTrack(i.toString, kind, text(t.language), text(t.label), if (text(t.kind) == "captions") "CC" else "")
  }

  /** The element's own text track chosen ("" none), once one is. */
  def ownChosen: Option[String] = ownText

  /** The element's own sound track `audio` (its place, -1 to leave it) heard, and text track `text` shown, once the
    * element lists them again: a source opened again as it was (`WebMedia`, after a move). */
  def restoreOwn(audio: Int, text: Option[String]): Unit = {
    ownAudio = audio
    ownText = text
    enforce()
  }

  /** The place of the element's own sound track heard, -1 for none. */
  def ownHeard: Int = ownList(MediaTrack.Audio).indexWhere(_.enabled.asInstanceOf[Any] == true)

  /** The renditions of `kind` the master playlist names, where it was read. */
  def listed(kind: String): List[MediaTrack] =
    renditions.getOrElse(if (kind == MediaTrack.Audio) "AUDIO" else "SUBTITLES", Nil).zipWithIndex.map { case (r, i) =>
      MediaTrack(i.toString, kind, r.language, r.name)
    }

  /** The playlist's default rendition of `kind`, by its place. */
  def listedDefault(kind: String): Int =
    math.max(0, renditions.getOrElse(if (kind == MediaTrack.Audio) "AUDIO" else "SUBTITLES", Nil).indexWhere(_.default))

  /** The sidecars made, in the order given. */
  def side: List[MediaTrack] = made.collect { case (id, _, _) if id.startsWith("side") => id.drop(4).toInt }.sorted
    .map { i =>
      val f = files(i)
      MediaTrack("side" + i, MediaTrack.Text, f.language, label(f), "")
    }

  /** The closed captions among the element's own text tracks (hls.js puts a stream's there), ids `cc<n>`. */
  def captions: List[MediaTrack] = ownList(MediaTrack.Text).filter(t => text(t.kind) == "captions").zipWithIndex
    .map { case (t, i) => MediaTrack("cc" + i, MediaTrack.Text, text(t.language), text(t.label), "CC") }

  /** The captions `cc<n>` shown (or drawn: [[CueDrawing]]), the rest hidden (hidden, not disabled, so hls.js goes on
    * filling them). */
  def showCaptions(id: String): Unit = {
    caption = id
    captionList.zipWithIndex.foreach { case (t, i) =>
      mode(t, if ("cc" + i == id) CueDrawing.mode(chosen = true, drawn()) else "hidden")
    }
    changed()
  }

  /** The text track chosen among the made ones, the element's own and the stream's captions, for its cues to be drawn
    * ([[WebCues]]), where there is one. */
  def chosen: Option[js.Dynamic] =
    made.find(_._1 == shown).map(_._2.asInstanceOf[js.Dynamic].track)
      .orElse(ownText.flatMap(_.toIntOption).flatMap(ownList(MediaTrack.Text).lift))
      .orElse(Some(caption).filter(_.startsWith("cc")).flatMap(_.drop(2).toIntOption).flatMap(captionList.lift))
      .filter(t => !js.isUndefined(t) && t != null)

  /** The subtitle track hls.js reads without showing it (its `subtitleDisplay` off: the one it chose is `hidden`, the
    * rest disabled), where there is one. */
  def hiddenSubtitles: Option[js.Dynamic] =
    ownList(MediaTrack.Text).find(t => text(t.kind) == "subtitles" && t.mode.asInstanceOf[Any] == "hidden")

  private def captionList: List[js.Dynamic] = ownList(MediaTrack.Text).filter(t => text(t.kind) == "captions")

  /** The element's own track `id` of `kind` heard or shown, the rest not; "" leaves the sound alone, and shows no
    * text. Whether the sound heard changed. */
  def selectOwn(kind: String, id: String): Boolean =
    if (kind == MediaTrack.Audio) {
      val list = ownList(kind).zipWithIndex
      val changed = id.nonEmpty && list.exists { case (t, i) => (t.enabled.asInstanceOf[Any] == true) != (id == i.toString) }
      if (changed) list.foreach { case (t, i) => t.enabled = id == i.toString }
      changed
    }
    else {
      ownText = Some(id)
      enforce()
      false
    }

  /** The made track `id` shown (`side<n>`, `hls<n>`), now or once it is made, and the others not; "" shows none. */
  def show(id: String): Unit = {
    shown = id
    enforce()
  }

  /** The playlist's subtitle rendition `i` shown, read from its segments the first time (`HlsVtt`). A live one (its
    * playlist not whole) is `live`'s, where it takes it (hls.js, which reads it again as it grows); else what it holds
    * now is shown, and said. */
  def showListed(i: Int, headers: Map[String, String], live: () => Boolean): Unit = {
    show("hls" + i)
    if (!made.exists(_._1 == "hls" + i)) renditions.getOrElse("SUBTITLES", Nil).lift(i).foreach { r =>
      val at = round
      val address = WebTracks.resolve(r.uri, playlist)
      fetchText(address, headers, media => if (at == round) {
        val whole = HlsVtt.ended(media)
        if (whole || !live()) {
          if (!whole) everyui.scribe.warn("media: subtitles " + r.name + " are live: only what they hold now is shown")
          val segments = HlsVtt.segments(media).map(WebTracks.resolve(_, address))
          gather(segments, headers, parts => if (at == round && !made.exists(_._1 == "hls" + i))
            add("hls" + i, if (r.name.nonEmpty) r.name else if (r.language.nonEmpty) r.language else "Subtitles",
              r.language, HlsVtt.join(parts)))
        }
      })
    }
  }

  /** The modes EveryUI chose set again over the engine's own choice, only where they differ (so a change heard is not
    * made again): as the element loads, and as tracks are added, loaded or changed. The chosen one is `showing`, or
    * `hidden` where EveryUI draws its cues ([[CueDrawing]]), and they are drawn again (`changed`). */
  def enforce(): Unit = {
    val drawing = drawn()
    made.foreach { case (id, track, _) =>
      mode(track.asInstanceOf[js.Dynamic].track, CueDrawing.mode(id == shown, drawing))
    }
    ownText.foreach { id =>
      ownList(MediaTrack.Text).zipWithIndex.foreach { case (t, i) => mode(t, CueDrawing.mode(id == i.toString, drawing)) }
    }
    if (caption.nonEmpty) captionList.zipWithIndex.foreach { case (t, i) =>
      if ("cc" + i == caption) mode(t, CueDrawing.mode(chosen = true, drawing))
    }
    if (ownAudio >= 0 && ownList(MediaTrack.Audio).size > ownAudio) {
      selectOwn(MediaTrack.Audio, ownAudio.toString)
      ownAudio = -1
    }
    changed()
  }
  private def mode(t: js.Dynamic, to: String): Unit =
    if (!js.isUndefined(t) && t != null && t.mode.asInstanceOf[Any] != to) t.mode = to

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
        case Some(vtt) => add("side" + i, label(f), f.language, vtt)
      })
    }
  }

  // a `<track>` of WebVTT `vtt` (a `blob:` of the page's own) on the element as `id`, its mode as chosen
  private def add(id: String, label: String, language: String, vtt: String): Unit = {
    val blob = js.Dynamic.newInstance(js.Dynamic.global.Blob)(js.Array(vtt), js.Dynamic.literal(`type` = "text/vtt"))
    val address = js.Dynamic.global.URL.createObjectURL(blob).asInstanceOf[String]
    val track = dom.document.createElement("track").asInstanceOf[html.Element]
    track.id = WebTracks.prefix + id
    track.setAttribute("kind", "subtitles")
    track.setAttribute("label", label)
    if (language.nonEmpty) track.setAttribute("srclang", language)
    track.setAttribute("src", address)
    List("load", "cuechange").foreach(e => track.addEventListener(e, (_: dom.Event) => enforce()))
    el.appendChild(track)
    made = (id, track, address) :: made
    enforce()
  }

  private def unmake(which: String => Boolean): Unit = {
    made.filter(t => which(t._1)).foreach { case (_, track, address) =>
      if (track.parentNode != null) track.parentNode.removeChild(track)
      js.Dynamic.global.URL.revokeObjectURL(address)
    }
    made = made.filterNot(t => which(t._1))
  }

  private def label(f: MediaSidecar): String =
    if (f.label.nonEmpty) f.label else if (f.language.nonEmpty) f.language else "Subtitles"

  // the bodies of `urls`, in their order, to `got` once every one has answered ("" for one that failed)
  private def gather(urls: List[String], headers: Map[String, String], got: List[String] => Unit): Unit = {
    val bodies = js.Array[String](urls.map(_ => "")*)
    var left = urls.size
    def one(): Unit = {
      left -= 1
      if (left == 0) got(bodies.toList)
    }
    if (urls.isEmpty) got(Nil)
    urls.zipWithIndex.foreach { case (u, i) =>
      fetchText(u, headers, body => {
        bodies(i) = body
        one()
      }, () => one())
    }
  }

  // the body of `url` as text, to `got`; a failure said, and told to `failed`
  private def fetchText(url: String, headers: Map[String, String], got: String => Unit,
                        failed: () => Unit = () => ()): Unit = {
    val init = js.Dynamic.literal(headers = js.Dictionary(headers.toSeq*))
    val answered: js.Function1[js.Dynamic, js.Any] = r =>
      if (r.ok.asInstanceOf[Boolean]) r.text() else js.Promise.reject("HTTP " + r.status.toString)
    val read: js.Function1[js.Any, Unit] = body => got(body.toString)
    val refused: js.Function1[js.Any, Unit] = why => {
      everyui.scribe.warn("media: " + WebMedia.origin(url) + " could not be read for its tracks: " + why.toString)
      failed()
    }
    WebFetch(url, init).`then`(answered).`then`(read, refused)
  }

  private def text(value: js.Dynamic): String = if (js.isUndefined(value) || value == null) "" else value.toString
}

object WebTracks {
  /** The id each `<track>` of EveryUI's has, before its own ("side0", "hls1"). */
  val prefix: String = "everyui-side-"

  /** `address` against `base` (itself against the page's address), as the page resolves it (`URL`); as it is where it
    * cannot be. */
  def resolve(address: String, base: String): String =
    scala.util.Try {
      val page = js.Dynamic.newInstance(js.Dynamic.global.URL)(base, dom.window.location.href).href
      js.Dynamic.newInstance(js.Dynamic.global.URL)(address, page).href.toString
    }.getOrElse(address)
}
