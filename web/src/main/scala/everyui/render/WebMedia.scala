package everyui.render

import everyui.component.{MediaDiagnostics, MediaRetry, MediaSidecar, MediaTrack}
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
  // the centre of a surround source and the listening aids, through Web Audio once asked for (CenterFold)
  private var centre = 0.0
  private var clarity = 0.0
  private var night = 0.0
  private var allowed = false
  private lazy val fold = new CenterFold(el, () => tell("mixing"))
  // the element's own tracks, the playlist's where the player lists none, and the sidecar files (WebTracks)
  private val extra = new WebTracks(el)
  // where the source opened is to start, set again until the player has played on from there (ResumeSeek)
  private var landing = new ResumeSeek(0.0)
  // a track chosen from the playlist where the element plays it without listing it: the source is opened again through
  // hls.js, there, with it (kind, place)
  private var pick: Option[(String, Int)] = None

  el.addEventListener("error", (_: dom.Event) => refused())
  // the browser pausing it of itself while it is meant to play: taken off the page (a media element is paused as it is
  // removed, and never started again by being put back), or started while it was off the page, which a browser
  // refuses; it plays on once it is on the page again, and the page is in sight. A pause while it is on the page (a
  // media key, the browser's own controls) is the person's, and stands
  el.addEventListener("pause", (_: dom.Event) => if (meantToPlay && !WebFeatures.connected(el)) onThePage())
  el.addEventListener("playing", (_: dom.Event) => {
    tries = 0
    played = true
  })
  // the start set again where the player dropped it
  for (event <- List("loadedmetadata", "canplay", "playing", "timeupdate"))
    el.addEventListener(event, (_: dom.Event) => resumed(event))

  /** Hear `error`, and `retry` while hls.js retries a load; the element's own events are heard where they happen. */
  def listen(f: String => Unit): Unit = listeners = listeners :+ f

  /** Play `to`, or nothing for "". The same address again is nothing. */
  def source(to: String): Unit =
    if (to.isEmpty) {
      close()
      tell("mixing")
    }
    else if (!url.contains(to)) {
      pick = None
      val at = start
      start = 0.0
      open(to, at, through = false)
    }

  // `to` opened `at` seconds in, through hls.js where it is HLS and hls.js is to play it (or `through` says so)
  private def open(to: String, at: Double, through: Boolean): Unit = {
    close()
    url = Some(to)
    from = at
    landing = new ResumeSeek(at)
    WebMedia.holding += this
    val scripted = WebMedia.hls(to) && (through || script)
    extra.opened(to, !scripted, headers)
    if (scripted) attach(to, opened) else native(to)
  }

  /** Subtitle files to offer beside the source's own tracks (`MediaSidecar`, [[WebTracks]]). */
  def sidecars(list: List[MediaSidecar]): Unit = extra.sidecars(list, url.nonEmpty, headers)

  /** Seconds in, for saving: where it was asked to start until it has played on from there. */
  def position: Double = landing.position(m.currentTime.asInstanceOf[Double])

  /** Sought to `seconds`, the viewer's or the app's: a start not yet landed is given up. */
  def seek(seconds: Double): Unit =
    // before it has loaded, where an older engine refuses a time, it is a start to land at instead
    if (!placed(seconds)) landing = new ResumeSeek(seconds)
    else landing.cancel()

  // the playhead set to `seconds`, and whether the engine took it: before Chromium 39 or so, and on a TV, a time set
  // before the element has loaded anything (HAVE_NOTHING) throws
  private def placed(seconds: Double): Boolean =
    try {
      m.currentTime = seconds
      true
    } catch { case _: Throwable => false }

  // the player's word as it opens: the start set again while it stands short of it
  private def resumed(event: String): Unit = if (url.nonEmpty) {
    landing.heard(event, m.currentTime.asInstanceOf[Double], m.seeking.asInstanceOf[Boolean]).foreach { at =>
      if (event != "loadedmetadata") everyui.scribe.info("media: starting at " + at.toInt + " s again, the player at " +
        m.currentTime.asInstanceOf[Double].toInt + " s (" + event + ")")
      placed(at)
    }
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

  /** Voice clarity at `c` and night mode at `n`, 0 to 1 each (MediaSupport.voiceClarity and nightMode), in the same
    * graph as the centre ([[ListeningNodes]]). */
  def aids(c: Double, n: Double): Unit = {
    clarity = c
    night = n
    fold.listening(c, n, hearable)
  }

  /** Whether the aids are in what is heard now: the sound taken into Web Audio. */
  def aidsHeard: Boolean = fold.aidsHeard

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

  /** The tracks: hls.js's sound tracks and subtitles and the stream's closed captions where hls.js plays it; else the
    * element's own, or, for an HLS stream it plays without listing them, the master playlist's; then the sidecars
    * ([[WebTracks]]). */
  def tracks: List[MediaTrack] = hls match {
    case Some(h) => listed(h.audioTracks, MediaTrack.Audio) ++ listed(h.subtitleTracks, MediaTrack.Text) ++
      extra.captions ++ extra.side
    case None =>
      val audio = extra.own(MediaTrack.Audio)
      val text = extra.own(MediaTrack.Text)
      (if (audio.nonEmpty) audio else extra.listed(MediaTrack.Audio)) ++
        (if (text.nonEmpty) text else extra.listed(MediaTrack.Text)) ++ extra.side
  }

  /** The track `id` heard or read; "" leaves the audio alone and shows no text. A stream's closed captions (`cc<n>`)
    * are the element's own tracks, hls.js's subtitles its, a sidecar (`side<n>`) a `<track>` of its own, and one
    * showing hides the others. A track the element plays without listing (the playlist's) opens the stream again
    * through hls.js, where it is, with that track. */
  def select(kind: String, id: String): Unit = {
    val audio = kind == MediaTrack.Audio
    if (!audio) extra.showSide(if (id.startsWith("side")) id else "")
    val i = if (id.startsWith("side")) -1 else id.toIntOption.getOrElse(-1)
    hls match {
      case Some(h) =>
        if (audio) { if (i >= 0) h.audioTrack = i }
        else {
          h.subtitleTrack = i
          h.subtitleDisplay = i >= 0
          extra.showCaptions(id)
        }
      case None =>
        val own = extra.own(kind)
        if (own.nonEmpty || (!audio && i < 0)) extra.selectOwn(kind, if (i < 0) "" else id)
        else if (i >= 0 && i < extra.listed(kind).size && (audio && i != extra.listedDefault(kind) || !audio))
          url.foreach { to =>
            if (!WebMedia.mse) everyui.scribe.warn("media: the player lists no " + kind + " tracks, and there is no " +
              "Media Source here to choose one through hls.js")
            else {
              everyui.scribe.info("media: " + kind + " track " + i + " of the playlist chosen: opening it again " +
                "through hls.js at " + position.toInt + " s")
              val at = position
              open(to, at, through = true)
              pick = Some(kind -> i)
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
    if (from > 0.0) placed(from)
    if (playing) resume()
    settled()
  }

  // a source open, through either player: the centre folded if it was asked for and can be, and the app told
  private def settled(): Unit = {
    fold.level(centre, hearable)
    fold.listening(clarity, night, hearable)
    tell("mixing")
  }

  // (`play()` answers a promise from Chromium 50; nothing before it)
  private def resume(): Unit = {
    val started = m.play()
    if (!js.isUndefined(started) && started != null)
      started.asInstanceOf[js.Promise[Unit]].`catch`((_: Any) => if (meantToPlay) onThePage())
  }

  // whether it is meant to be playing and is not: something to play, asked to play, not at its end and not failed
  private def meantToPlay: Boolean = url.nonEmpty && playing && failure.isEmpty && m.paused.asInstanceOf[Boolean] &&
    !m.ended.asInstanceOf[Boolean]
  // how many times it was started again on the page without playing: a browser refusing it for good is let be
  private var tries = 0
  private var waitingForPage = false

  /** Started again once it is on the page with the page in sight, looked at every few frames until it is. */
  private def onThePage(): Unit = if (!waitingForPage && tries < WebMedia.restarts) {
    waitingForPage = true
    dom.window.setTimeout(() => {
      waitingForPage = false
      if (meantToPlay) {
        if (WebFeatures.connected(el) && dom.document.visibilityState != "hidden") {
          tries += 1
          resume()
        }
        else onThePage()
      }
    }, WebMedia.restartWait)
  }

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
    val init = js.Dynamic.literal(headers = js.Dictionary(headers.toSeq*))
    val abort = WebFeatures.abortable(init)
    val answered: js.Function1[js.Dynamic, Unit] = r => {
      val kind = Option(r.headers.get("content-type").asInstanceOf[String]).getOrElse("").toLowerCase
      abort(r)
      if (at == opened) {
        if (kind.contains("mpegurl")) attach(to, at) else fail(nativeError)
      }
    }
    val failed: js.Function1[js.Any, Unit] = _ => if (at == opened) fail(nativeError)
    WebFetch(to, init).`then`(answered, failed)
  }

  private def nativeError: String = {
    val e = m.error
    if (js.isUndefined(e) || e == null) "media error"
    else "MediaError " + e.code.toString + Some(text(e.message)).filter(_.nonEmpty).map(": " + _).getOrElse("")
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
        WebMedia.scripts += 1
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
  // (a track picked from the playlist before hls.js played it is the choice over the languages)
  private def heard(h: js.Dynamic): Unit = js.timers.setTimeout(0) {
    if (hls.exists(_ eq h)) picked(MediaTrack.Audio).orElse(chosen(h.audioTracks, wanted._1)).foreach(i => h.audioTrack = i)
  }
  private def read(h: js.Dynamic): Unit = js.timers.setTimeout(0) {
    if (hls.exists(_ eq h)) picked(MediaTrack.Text).orElse(chosen(h.subtitleTracks, wanted._2)).foreach { i =>
      h.subtitleTrack = i
      h.subtitleDisplay = true
    }
  }

  private def picked(kind: String): Option[Int] = pick.filter(_._1 == kind).map(_._2)

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

  /** What it held let go, the same however often it is asked: hls.js destroyed (and with it its MediaSource), the
    * sidecars' `<track>`s taken off, and the element paused, its `src` and any `<source>` taken away and loaded empty,
    * which is what lets a TV's player give its decoder back. The element itself goes with its component. */
  private def close(): Unit = {
    opened += 1
    val held = url.nonEmpty || hls.nonEmpty || el.hasAttribute("src") || extra.held > 0 ||
      el.querySelector("source") != null
    WebMedia.holding -= this
    landing = new ResumeSeek(0.0)
    played = false
    url = None
    tries = 0
    allowed = false
    fold.fresh()
    probed = false
    recovered = false
    measured = false
    retries = 0
    retry = None
    failure = ""
    hls.foreach { h =>
      h.destroy()
      WebMedia.scripts -= 1
    }
    hls = None
    extra.clear()
    if (held) {
      m.pause()
      el.removeAttribute("src")
      val sources = el.querySelectorAll("source")
      (0 until sources.length).foreach(i => el.removeChild(sources(i)))
      m.load()
    }
  }

  /** Its end, as the element says it: the end where the source played, and otherwise an error, as on every platform
    * (a source that never played has not been watched). */
  def ended(f: String => Unit): Unit = if (played) f("ended") else fail("ended without playing")
  private var played = false

  private def fail(why: String): Unit = {
    failure = why
    tell("error")
  }

  private def tell(event: String): Unit = listeners.foreach(_(event))

  private def text(value: js.Dynamic): String =
    if (js.isUndefined(value) || value == null) "" else value.toString
}

object WebMedia {
  // the players holding a source, and hls.js's players (each with its MediaSource) not yet destroyed
  private[render] var holding: Set[WebMedia] = Set.empty
  private[render] var scripts: Int = 0

  /** What the page's media elements hold now: how many have a source open, and how many hls.js players (each with
    * its MediaSource and buffers) are alive. Both 0 once every player is let go: a check of teardown, for a spec,
    * the memory check and the player check. */
  def held: (Int, Int) = (holding.size, scripts)

  /** How many times a source the browser paused of itself is started again before it is let be, and how long each wait
    * for it to be on the page in sight is, in ms. */
  private val restarts: Int = 20
  private val restartWait: Int = 120

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
    val init = js.Dynamic.literal(mode = "cors", credentials = "omit", headers = js.Dictionary(headers.toSeq*))
    val abort = WebFeatures.abortable(init)
    val answered: js.Function1[js.Dynamic, Unit] = r => {
      abort(r)
      answer(r.ok.asInstanceOf[Boolean])
    }
    val failed: js.Function1[js.Any, Unit] = _ => answer(false)
    WebFetch(url, init).`then`(answered, failed)
  }

  /** Whether the browser has Media Source Extensions (Safari on an iPhone, the managed kind). */
  def mse: Boolean = {
    val window = dom.window.asInstanceOf[js.Dynamic]
    !js.isUndefined(window.MediaSource) || !js.isUndefined(window.ManagedMediaSource)
  }

  /** Whether the browser plays HLS in `media` itself and lists its tracks, as Safari does; or a TV's runtime plays it
    * itself, which is preferred there (`AppHost.nativeHls`: webOS), whether or not it lists its sound tracks. */
  def nativeHls(media: js.Dynamic): Boolean =
    media.canPlayType("application/vnd.apple.mpegurl").asInstanceOf[String].nonEmpty &&
      (!js.isUndefined(media.audioTracks) || AppHosts.current.exists(_.nativeHls))
}
