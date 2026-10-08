package everyui.render

import everyui.component.{MediaDiagnostics, MediaRetry, MediaTrack}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** What a `video` or `audio` element plays (Nodes.video, Nodes.audio): the browser's own player for what it plays
  * itself, and hls.js ([[HlsScript]]) over Media Source Extensions for an HLS stream where it has no HLS of its own,
  * or none that lists its tracks.
  *
  * A source is HLS by its address (a path ending `.m3u8`), or, where the browser refuses a source, by the type its
  * server answers for it. Where the browser plays HLS itself and lists its tracks (Safari), it plays it; where it plays
  * HLS but lists no tracks (recent Chrome), where it has none (Firefox), and wherever there are [[headers]] to send,
  * hls.js plays it: it sends them with every playlist, segment and key it asks for. The browser's own player sends
  * none, so a plain file (an `.mp4`) can carry what it needs only in its address. hls.js is loaded the first time a
  * source needs it, and each source's player is destroyed when the source changes or goes. */
final class WebMedia(el: html.Element) {
  private val m = el.asInstanceOf[js.Dynamic]

  /** Sent with every request hls.js makes for the next source. */
  var headers: Map[String, String] = Map.empty
  /** Where the next source starts, in seconds. */
  var start: Double = 0.0
  /** How much hls.js holds ahead for the next source: `responsive`, `balanced` or `slow`. */
  var buffering: String = "balanced"
  /** The load hls.js is retrying on its own, while it is. */
  var retry: Option[MediaRetry] = None

  private var playing = true
  private var url: Option[String] = None
  private var from = 0.0
  private var hls: Option[js.Dynamic] = None
  // each source opened is a new one, so what an older one's script, probe or player answers late is ignored
  private var opened = 0
  private var probed = false
  private var recovered = false
  private var measured = false
  private var retries = 0
  private var failure = ""
  private var wanted = ("", "")
  private var listeners: List[String => Unit] = Nil
  // the centre of a surround source, folded through Web Audio once asked for (CenterFold)
  private var centre = 0.0
  private var allowed = false
  private lazy val fold = new CenterFold(el, () => tell("mixing"))

  el.addEventListener("error", (_: dom.Event) => refused())

  /** Hear `error`, and `retry` while hls.js retries a load; the element's own events are heard where they happen. */
  def listen(f: String => Unit): Unit = listeners = listeners :+ f

  /** Play `to`, or nothing for "". The same address again is nothing. */
  def source(to: String): Unit =
    if (to.isEmpty) {
      close()
      tell("mixing")
    }
    else if (!url.contains(to)) {
      close()
      url = Some(to)
      from = start
      start = 0.0
      if (WebMedia.hls(to) && script) attach(to, opened) else native(to)
    }

  /** The centre of a surround source at `db` dB over the usual (MediaSupport.centerMix). */
  def centerMix(db: Double): Unit = {
    centre = db
    fold.level(db, hearable)
  }

  /** Whether what plays is Web Audio's to hear: a stream through hls.js (a `blob:` of the page's own), a file from the
    * page's origin, or one from another that allows it. */
  def hearable: Boolean = url.nonEmpty && (hls.nonEmpty || allowed || url.exists(WebMedia.sameOrigin))

  /** Whether the centre can be folded here for what plays: Web Audio, and nothing playing or something it may hear. */
  def centerMixes: Boolean = CenterFold.available && (url.isEmpty || hearable || WebMedia.hls(url.getOrElse("")) && script)

  /** Whether the centre is being folded now. */
  def centerMixing: Boolean = fold.mixing

  def play(on: Boolean): Unit = {
    playing = on
    if (!on) m.pause()
    else if (url.nonEmpty) resume()
  }

  /** What to hear and read the next HLS source in (a language tag each, or ""), chosen as it opens. */
  def languages(audio: String, text: String): Unit = wanted = (audio, text)

  /** Whether it is waiting for data with playing asked for (and nothing has failed). */
  def waiting: Boolean =
    url.nonEmpty && failure.isEmpty && playing && !m.paused.asInstanceOf[Boolean] && !m.ended.asInstanceOf[Boolean] &&
      m.readyState.asInstanceOf[Int] < 3

  /** What hls.js has measured the link carrying, in bits a second: 0 before a segment has come, and for the browser's
    * own player. */
  def bandwidth: Long = hls.filter(_ => measured).map { h =>
    val b = h.bandwidthEstimate.asInstanceOf[Double]
    if (b.isNaN || b <= 0.0) 0L else b.toLong
  }.getOrElse(0L)

  def tracks: List[MediaTrack] = hls match {
    case Some(h) => listed(h.audioTracks, MediaTrack.Audio) ++ listed(h.subtitleTracks, MediaTrack.Text) ++ captions
    // the browser lists the tracks a file carries; `audioTracks` is not in every engine, so a missing list is no list
    case None => owned("audioTracks", MediaTrack.Audio) ++ owned("textTracks", MediaTrack.Text)
  }

  /** The track `id` heard or read; "" leaves the audio alone and shows no text. A stream's closed captions (`cc<n>`)
    * are the element's own tracks, hls.js's subtitles its, and one showing hides the other. */
  def select(kind: String, id: String): Unit = {
    val audio = kind == MediaTrack.Audio
    hls match {
      case Some(h) =>
        val i = id.toIntOption.getOrElse(-1)
        if (audio) { if (i >= 0) h.audioTrack = i }
        else {
          h.subtitleTrack = i
          h.subtitleDisplay = i >= 0
          // hidden rather than disabled, so hls.js goes on filling them
          captions.foreach { t =>
            show(t.id.drop(2), if (t.id == id) "showing" else "hidden")
          }
        }
      case None => if (!audio || id.nonEmpty) {
        val list = m.selectDynamic(if (audio) "audioTracks" else "textTracks")
        if (!js.isUndefined(list) && list != null) {
          (0 until list.length.asInstanceOf[Int]).foreach { i =>
            val t = list.selectDynamic(i.toString)
            val on = id == i.toString
            if (audio) t.enabled = on else t.mode = if (on) "showing" else "disabled"
          }
        }
      }
    }
  }

  /** What hls.js is decoding, and the last error either player met (with no address: an address carries a token). */
  def diagnostics: MediaDiagnostics = hls match {
    case Some(h) =>
      val levels = h.levels.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].getOrElse(js.Array())
      val at = h.currentLevel.asInstanceOf[Int]
      val level = if (at >= 0 && at < levels.length) Some(levels(at)) else None
      def codec(field: String): String = level.map(l => text(l.selectDynamic(field))).getOrElse("")
      val video = level.map { l =>
        val c = codec("videoCodec")
        if (c.isEmpty) "none" else "video/" + c + " " + l.width.toString + "x" + l.height.toString
      }.getOrElse("")
      val audio = level.map(_ => codec("audioCodec")).map(c => if (c.isEmpty) "" else "audio/" + c).getOrElse("")
      val chosen = h.audioTrack.asInstanceOf[Int]
      val languages = listed(h.audioTracks, MediaTrack.Audio).map { t =>
        (if (t.language.isEmpty) t.id else t.language) + (if (t.id == chosen.toString) "*" else "") +
          (if (t.label.isEmpty) "" else "(" + t.label + ")")
      }.mkString(",")
      MediaDiagnostics(audio, video, languages, failure)
    case None => MediaDiagnostics(failure = failure)
  }

  // ---- the browser's own player ----

  // a file from another origin, to an element whose sound Web Audio has taken, is played as `cors` where its server
  // allows the page, and is heard; where it does not, it plays without a sound, which is said
  private def native(to: String): Unit =
    if (!fold.taken || WebMedia.sameOrigin(to)) {
      el.removeAttribute("crossorigin")
      load(to)
    }
    else {
      val at = opened
      WebMedia.allows(to, headers, ok => if (at == opened) {
        allowed = ok
        if (ok) el.setAttribute("crossorigin", "anonymous")
        else {
          el.removeAttribute("crossorigin")
          everyui.scribe.error("media: centre mix: " + WebMedia.origin(to) + " does not allow this page to hear what it " +
            "sends (CORS), and this element's sound already goes through Web Audio: it plays without a sound")
        }
        load(to)
      })
    }

  private def load(to: String): Unit = {
    el.setAttribute("src", to)
    // before the file has said anything, a time set is where it starts once it has (its default playback start
    // position), rather than a seek after it
    if (from > 0.0) m.currentTime = from
    if (playing) resume()
    settled()
  }

  // a source open, through either player: the centre folded if it was asked for and can be, and the app told
  private def settled(): Unit = {
    fold.level(centre, hearable)
    tell("mixing")
  }

  private def resume(): Unit = m.play().asInstanceOf[js.Promise[Unit]].`catch`((_: Any) => ())

  // the element's own error: a source it cannot play may be HLS it does not know by its address, so the server is
  // asked what it is, once, before the error is said; hls.js's errors are its own
  private def refused(): Unit = if (hls.isEmpty) url.foreach { to =>
    if (!probed && WebMedia.mse && !WebMedia.hls(to)) {
      probed = true
      probe(to, opened)
    }
    else fail(nativeError)
  }

  // only the answer's headers are wanted, so the body is let go as soon as they are in
  private def probe(to: String, at: Int): Unit = {
    val abort = js.Dynamic.newInstance(js.Dynamic.global.AbortController)()
    val init = js.Dynamic.literal(headers = js.Dictionary(headers.toSeq*), signal = abort.signal)
    val answered: js.Function1[js.Dynamic, Unit] = r => {
      val kind = Option(r.headers.get("content-type").asInstanceOf[String]).getOrElse("").toLowerCase
      abort.abort()
      if (at == opened) {
        if (kind.contains("mpegurl")) attach(to, at) else fail(nativeError)
      }
    }
    val failed: js.Function1[js.Any, Unit] = _ => if (at == opened) fail(nativeError)
    js.Dynamic.global.fetch(to, init).`then`(answered, failed)
  }

  private def nativeError: String = {
    val e = m.error
    if (js.isUndefined(e) || e == null) "media error"
    else "MediaError " + e.code.toString + Some(text(e.message)).filter(_.nonEmpty).map(": " + _).getOrElse("")
  }

  // the closed captions hls.js found in the video, each a `captions` track of the element's own
  private def captions: List[MediaTrack] = {
    val list = m.textTracks
    (0 until list.length.asInstanceOf[Int]).toList.flatMap { i =>
      val t = list.selectDynamic(i.toString)
      if (text(t.kind) != "captions") None
      else Some(MediaTrack("cc" + i, MediaTrack.Text, text(t.language), text(t.label), "CC"))
    }
  }

  private def show(index: String, mode: String): Unit =
    index.toIntOption.foreach(i => m.textTracks.selectDynamic(i.toString).mode = mode)

  private def owned(field: String, kind: String): List[MediaTrack] = {
    val list = m.selectDynamic(field)
    if (js.isUndefined(list) || list == null) Nil
    else (0 until list.length.asInstanceOf[Int]).toList.map { i =>
      val t = list.selectDynamic(i.toString)
      MediaTrack(i.toString, kind, text(t.language), text(t.label))
    }
  }

  // ---- hls.js ----

  // where hls.js plays it: with Media Source Extensions, and where the browser does not play HLS itself as well (and
  // list its tracks), or there are headers to send
  private def script: Boolean = WebMedia.mse && (headers.nonEmpty || !WebMedia.nativeHls(m))

  private def attach(to: String, at: Int): Unit = HlsScript.load { loaded =>
    if (at == opened) {
      if (!loaded) fail("hls.js could not be loaded")
      else if (!HlsScript.hls.isSupported().asInstanceOf[Boolean]) native(to)
      else {
        // a probe leaves the refused source behind
        el.removeAttribute("src")
        val h = js.Dynamic.newInstance(HlsScript.hls)(config())
        hls = Some(h)
        settled()
        val events = HlsScript.hls.Events
        h.on(events.MANIFEST_PARSED, ((_: js.Any, _: js.Any) => parsed()): js.Function2[js.Any, js.Any, Unit])
        h.once(events.AUDIO_TRACKS_UPDATED, ((_: js.Any, _: js.Any) => heard(h)): js.Function2[js.Any, js.Any, Unit])
        h.once(events.SUBTITLE_TRACKS_UPDATED, ((_: js.Any, _: js.Any) => read(h)): js.Function2[js.Any, js.Any, Unit])
        h.on(events.FRAG_LOADED, ((_: js.Any, _: js.Any) => measured = true): js.Function2[js.Any, js.Any, Unit])
        h.on(events.ERROR, ((_: js.Any, data: js.Dynamic) => error(h, data)): js.Function2[js.Any, js.Dynamic, Unit])
        h.loadSource(to)
        h.attachMedia(m)
      }
    }
  }

  private def config(): js.Dynamic = {
    val sent = headers
    // seconds held ahead, and the most it may grow to: hls.js's own for balanced
    val (ahead, most) = buffering match {
      case "responsive" => (10, 30)
      case "slow" => (60, 600)
      case _ => (30, 600)
    }
    val xhr: js.Function2[js.Dynamic, String, Unit] = (x, _) => sent.foreach(h => x.setRequestHeader(h._1, h._2))
    val fetch: js.Function2[js.Dynamic, js.Dynamic, js.Dynamic] = (context, init) => {
      val passed = init.headers.asInstanceOf[js.UndefOr[js.Any]].getOrElse(js.Dictionary())
      val all = js.Dynamic.newInstance(js.Dynamic.global.Headers)(passed)
      sent.foreach(h => all.set(h._1, h._2))
      init.headers = all
      js.Dynamic.newInstance(js.Dynamic.global.Request)(context.url, init)
    }
    js.Dynamic.literal(
      startPosition = if (from > 0.0) from else -1.0,
      workerPath = HlsScript.worker,
      maxBufferLength = ahead,
      maxMaxBufferLength = most,
      xhrSetup = xhr,
      fetchSetup = fetch)
  }

  private def parsed(): Unit = if (playing) resume()

  // the tracks in the languages asked for, the first time each list is known: just after, as hls.js makes its own
  // choice once it has told of them
  private def heard(h: js.Dynamic): Unit = js.timers.setTimeout(0) {
    if (hls.exists(_ eq h)) chosen(h.audioTracks, wanted._1).foreach(i => h.audioTrack = i)
  }
  private def read(h: js.Dynamic): Unit = js.timers.setTimeout(0) {
    if (hls.exists(_ eq h)) chosen(h.subtitleTracks, wanted._2).foreach { i =>
      h.subtitleTrack = i
      h.subtitleDisplay = true
    }
  }

  // a failed load hls.js will try again is a retry; a media error is recovered from once; anything else fatal ends it
  private def error(h: js.Dynamic, data: js.Dynamic): Unit = if (hls.exists(_ eq h)) {
    val kind = text(data.`type`)
    val details = text(data.details)
    val response = data.response
    val code = if (js.isUndefined(response) || response == null || js.isUndefined(response.code)) ""
      else " " + response.code.toString
    if (!data.fatal.asInstanceOf[Boolean]) {
      if (kind == "networkError") {
        retries += 1
        retry = Some(MediaRetry(retries, host(data), details + code))
        tell("retry")
      }
    }
    else if (kind == "mediaError" && !recovered) {
      recovered = true
      h.recoverMediaError()
    }
    else fail(kind + " " + details + code)
  }

  private def host(data: js.Dynamic): String = {
    val frag = data.frag
    val context = data.context
    val url = if (!js.isUndefined(frag) && frag != null) text(frag.url)
      else if (!js.isUndefined(context) && context != null) text(context.url)
      else text(data.url)
    if (url.isEmpty) ""
    else scala.util.Try(js.Dynamic.newInstance(js.Dynamic.global.URL)(url).host.toString).getOrElse("")
  }

  private def listed(list: js.Dynamic, kind: String): List[MediaTrack] =
    if (js.isUndefined(list) || list == null) Nil
    else list.asInstanceOf[js.Array[js.Dynamic]].toList.zipWithIndex.map { case (t, i) =>
      MediaTrack(i.toString, kind, text(t.lang), text(t.name))
    }

  // the first track in the language, by its two-letter tag
  private def chosen(list: js.Dynamic, language: String): Option[Int] =
    if (language.isEmpty) None
    else listed(list, "").map(_.language).indexWhere(l => l.nonEmpty &&
      MediaTrack.languageTag(l) == MediaTrack.languageTag(language)) match {
      case -1 => None
      case i => Some(i)
    }

  // ---- either ----

  private def close(): Unit = {
    opened += 1
    url = None
    allowed = false
    fold.fresh()
    probed = false
    recovered = false
    measured = false
    retries = 0
    retry = None
    failure = ""
    hls.foreach(_.destroy())
    hls = None
    m.pause()
    el.removeAttribute("src")
    m.load()
  }

  private def fail(why: String): Unit = {
    failure = why
    tell("error")
  }

  private def tell(event: String): Unit = listeners.foreach(_(event))

  private def text(value: js.Dynamic): String =
    if (js.isUndefined(value) || value == null) "" else value.toString
}

object WebMedia {
  /** Whether an address is an HLS playlist's: its path ends `.m3u8`. */
  def hls(url: String): Boolean = url.takeWhile(c => c != '?' && c != '#').toLowerCase.endsWith(".m3u8")

  /** Whether `url` is the page's own to hear: from its origin, or a `blob:` or `data:` of its own. */
  def sameOrigin(url: String): Boolean = {
    val lower = url.toLowerCase
    lower.startsWith("blob:") || lower.startsWith("data:") || origin(url) == dom.window.location.origin
  }

  /** The origin `url` is from, against the page's address. */
  def origin(url: String): String =
    scala.util.Try(js.Dynamic.newInstance(js.Dynamic.global.URL)(url, dom.window.location.href).origin.toString)
      .getOrElse("")

  /** Whether the server of `url` allows the page to read what it sends (CORS), asked with a request whose answer is
    * let go as soon as its headers are in: `answer` hears true where it does, and false where it does not or the
    * request fails. */
  def allows(url: String, headers: Map[String, String], answer: Boolean => Unit): Unit = {
    val abort = js.Dynamic.newInstance(js.Dynamic.global.AbortController)()
    val init = js.Dynamic.literal(mode = "cors", credentials = "omit", headers = js.Dictionary(headers.toSeq*),
      signal = abort.signal)
    val answered: js.Function1[js.Dynamic, Unit] = r => {
      abort.abort()
      answer(r.ok.asInstanceOf[Boolean])
    }
    val failed: js.Function1[js.Any, Unit] = _ => answer(false)
    js.Dynamic.global.fetch(url, init).`then`(answered, failed)
  }

  /** Whether the browser has Media Source Extensions (Safari on an iPhone, the managed kind). */
  def mse: Boolean = {
    val window = dom.window.asInstanceOf[js.Dynamic]
    !js.isUndefined(window.MediaSource) || !js.isUndefined(window.ManagedMediaSource)
  }

  /** Whether the browser plays HLS in `media` itself and lists its tracks, as Safari does. */
  def nativeHls(media: js.Dynamic): Boolean =
    media.canPlayType("application/vnd.apple.mpegurl").asInstanceOf[String].nonEmpty &&
      !js.isUndefined(media.audioTracks)
}
