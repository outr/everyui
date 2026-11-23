package everyui.render

import everyui.component.{MediaDiagnostics, MediaRetry, MediaSidecar, MediaTrack}
import everyui.text.CaptionStyle
import org.scalajs.dom
import org.scalajs.dom.html
import reactify.Var

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
  * source needs it, and each source's player is destroyed when the source changes or goes.
  *
  * Moved on the page (put in another box, or a box holding it moved: `RenderNode.place`), it is looked at at once and
  * a moment after ([[MoveReset]]): where the engine let the source go in the move (webOS 3.5), it is opened again where
  * it was, with its tracks, its sound and its rate as they were, playing or paused as it was (a brief wait, then
  * playing); where the engine only paused it (Chromium 38 pauses an element taken off the page, and nothing starts it
  * again), it plays on; and its boxes are marked again ([[VideoPath]]).
  *
  * Where the browser's own subtitles would not be seen over the picture (a TV: [[CueDrawing]]), the chosen text track
  * is `hidden` rather than `showing` and EveryUI draws its cues ([[WebCues]]), as it plays, seeks and moves; `drawCues`
  * forces either way, and `cueStyle` gives them a look. */
final class WebMedia(private[render] val el: html.Element, path: Option[VideoPath] = None) {
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
  // the subtitles where EveryUI draws them (WebCues, CueDrawing)
  private val cues = new WebCues(el)
  // the element's own tracks, the playlist's where the player lists none, and the sidecar files (WebTracks)
  private val extra = new WebTracks(el, () => WebMedia.drawsCues, () => cued())
  // where the source opened is to start, set again until the player has played on from there (ResumeSeek)
  private var landing = new ResumeSeek(0.0)
  // the chosen text track's cues drawn where EveryUI draws them (CueDrawing), and else no box held: hls.js's subtitle
  // track is the one it reads hidden
  private[render] def cued(): Unit =
    if (url.nonEmpty && WebMedia.drawsCues)
      cues.follow(extra.chosen.orElse(hls.filter(_.subtitleTrack.asInstanceOf[Int] >= 0).flatMap(_ => extra.hiddenSubtitles))
        .orNull)
    else cues.clear()

  // `WebMedia.drawCues` changed: hls.js's subtitles shown or hidden as it says, the modes set again, the cues drawn
  private[render] def redrawn(): Unit = {
    hls.foreach(h => if (h.subtitleTrack.asInstanceOf[Int] >= 0) h.subtitleDisplay = !WebMedia.drawsCues)
    extra.enforce()
  }

  // a track chosen from the playlist where the element plays it without listing it: the source is opened again through
  // hls.js, there, with it (its kind to its place); and after a move let its source go, hls.js's own choices
  private var pick: Map[String, Int] = Map.empty
  // whether the source opened has loaded (its metadata in), and where it was last heard to be while it had
  private var loaded = false
  private var known = 0.0
  // each move a new one, and each seek or track chosen: a look after a move made before it is let go
  private var moves = 0
  // the volume, muting and rate to put back as a source opened again after a move loads (`restore`), and how many
  // times one has been (on the element as `data-everyui-restores`, for a look from a TV's inspector)
  private var kept: Option[(Double, Boolean, Double)] = None
  private var restores = 0

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
  // the start, a seek's place or a sound track's set again where the player dropped it (a seek is followed by a
  // timeupdate), and the text tracks' modes set again as it loads
  for (event <- List("loadedmetadata", "canplay", "playing", "timeupdate"))
    el.addEventListener(event, (_: dom.Event) => resumed(event))
  el.addEventListener("loadedmetadata", (_: dom.Event) => if (url.nonEmpty) {
    loaded = true
    kept.foreach(put)
    kept = None
  })

  /** Hear `error`, and `retry` while hls.js retries a load; the element's own events are heard where they happen. */
  def listen(f: String => Unit): Unit = listeners = listeners :+ f

  /** Play `to`, or nothing for "". The same address again is nothing. */
  def source(to: String): Unit =
    if (to.isEmpty) {
      close()
      tell("mixing")
    }
    else if (!url.contains(to)) {
      pick = Map.empty
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
    if (WebMedia.hls(to) && headers.nonEmpty && !scripted) everyui.scribe.warn("media: " + WebMedia.origin(to) +
      " is played without its request headers: this engine's own player sends none, and hls.js cannot run here")
    extra.opened(to, !scripted, headers)
    if (scripted) attach(to, opened) else native(to)
  }

  /** Subtitle files to offer beside the source's own tracks (`MediaSidecar`, [[WebTracks]]). */
  def sidecars(list: List[MediaSidecar]): Unit = extra.sidecars(list, url.nonEmpty, headers)

  /** Seconds in, for saving: where it was asked to start until it has played on from there. */
  def position: Double = landing.position(m.currentTime.asInstanceOf[Double])

  /** Sought to `seconds`, the viewer's or the app's: a place to land on (ResumeSeek) in place of any other, as webOS 3's
    * player springs back from a seek in an MP4 now and then; `position` says it until it has. Before the element has
    * loaded, where an older engine refuses a time, it is set once the player says more. */
  def seek(seconds: Double): Unit = {
    placed(seconds)
    landing = new ResumeSeek(seconds)
    keeping = false
    known = seconds
    moves += 1
  }

  // the playhead set to `seconds`, and whether the engine took it: before Chromium 39 or so, and on a TV, a time set
  // before the element has loaded anything (HAVE_NOTHING) throws
  private def placed(seconds: Double): Boolean =
    try {
      m.currentTime = seconds
      true
    } catch { case _: Throwable => false }

  // the player's word as it opens, seeks or changes its sound: the place set again while it stands short of it, and
  // after a sound track chosen, playing or paused as it was
  private def resumed(event: String): Unit = if (url.nonEmpty) {
    // (the cues drawn again: `enforce` draws them, and a seek is followed by a timeupdate)
    if (event != "timeupdate") extra.enforce() else cued()
    if (m.readyState.asInstanceOf[Int] > 0 && !m.seeking.asInstanceOf[Boolean]) known = position
    landing.heard(event, m.currentTime.asInstanceOf[Double], m.seeking.asInstanceOf[Boolean]).foreach { at =>
      if (event != "loadedmetadata") everyui.scribe.info("media: landing at " + at.toInt + " s again, the player at " +
        m.currentTime.asInstanceOf[Double].toInt + " s (" + event + ")")
      placed(at)
    }
    if (landing.done) keeping = false
    else if (keeping) {
      if (!playing && !m.paused.asInstanceOf[Boolean]) m.pause()
      else if (meantToPlay) resume()
    }
  }
  // whether the player is kept playing or paused as it was while it lands (a sound track chosen on its own player)
  private var keeping = false

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
    * showing hides the others. A sound track of the element's own chosen lands where it was (a TV's player starts
    * again from 0), playing or paused as it was. A subtitle rendition the element plays without listing (the
    * playlist's) is read from its WebVTT segments and shown as a `<track>`, the player playing on as it was; a sound
    * rendition, or live subtitles, open the stream again through hls.js, where it is, with that track, once hls.js has
    * loaded (the element's own source kept until then, and kept where it cannot be). */
  def select(kind: String, id: String): Unit = {
    val audio = kind == MediaTrack.Audio
    moves += 1
    if (!audio) extra.show(if (id.startsWith("side")) id else "")
    val i = if (id.startsWith("side")) -1 else id.toIntOption.getOrElse(-1)
    hls match {
      case Some(h) =>
        if (audio) { if (i >= 0) h.audioTrack = i }
        else {
          h.subtitleTrack = i
          h.subtitleDisplay = i >= 0 && !WebMedia.drawsCues
          extra.showCaptions(id)
        }
      case None =>
        val own = extra.own(kind)
        if (own.nonEmpty || (!audio && i < 0)) {
          val at = position
          if (extra.selectOwn(kind, if (i < 0) "" else id)) {
            landing = new ResumeSeek(at)
            keeping = true
          }
        }
        else if (i >= 0 && i < extra.listed(kind).size) {
          if (!audio) extra.showListed(i, headers, () => reopen(kind, i))
          else if (i != extra.listedDefault(kind)) reopen(kind, i)
        }
    }
  }

  // the stream opened again through hls.js, where it is, with the playlist's track `i` of `kind`, once hls.js has
  // loaded; whether it will be. Where hls.js cannot run or load, the element plays on as it was, and it is said
  private def reopen(kind: String, i: Int): Boolean = url.exists { to =>
    val why = if (!WebMedia.mse) "there is no Media Source here" else if (!WebFeatures.hlsJs) "hls.js cannot run on " +
      "this engine (it needs Chromium 47's built-ins)" else ""
    if (why.nonEmpty) {
      everyui.scribe.warn("media: the player lists no " + kind + " tracks, and " + why + " to choose one through hls.js")
      false
    }
    else {
      val at = opened
      HlsScript.load { loaded =>
        if (at == opened && hls.isEmpty) {
          if (!loaded || !HlsScript.hls.isSupported().asInstanceOf[Boolean])
            everyui.scribe.warn("media: hls.js could not be loaded to choose " + kind + " track " + i + " of the " +
              "playlist: it plays on as it was")
          else {
            everyui.scribe.info("media: " + kind + " track " + i + " of the playlist chosen: opening it again " +
              "through hls.js at " + position.toInt + " s")
            open(to, position, through = true)
            pick += kind -> i
          }
        }
      }
      true
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
    case None =>
      // the element's own sound tracks, where it lists them, marked as hls.js's are
      val heard = extra.ownHeard
      val languages = extra.own(MediaTrack.Audio).map { t =>
        (if (t.language.isEmpty) t.id else t.language) + (if (t.id == heard.toString) "*" else "") +
          (if (t.label.isEmpty) "" else "(" + t.label + ")")
      }.mkString(",")
      MediaDiagnostics(audioTracks = languages, failure = failure)
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
    if (!probed && WebMedia.scriptable && !WebMedia.hls(to)) {
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
    val said = if (js.isUndefined(e) || e == null) "media error"
      else "MediaError " + e.code.toString + Some(text(e.message)).filter(_.nonEmpty).map(": " + _).getOrElse("")
    // an HLS stream that only hls.js could have played as asked: with its headers, or at all
    val stream = url.exists(WebMedia.hls)
    if (stream && !WebMedia.scriptable && headers.nonEmpty) said + " (the stream's request headers were not sent: " +
      "this engine's own player sends none, and hls.js, which does, cannot run here)"
    else if (stream && !WebMedia.scriptable && !WebMedia.nativeHls(m)) said + " (this engine plays no HLS: it has no " +
      "player of its own for it, and hls.js cannot run here)"
    else said
  }

  // ---- hls.js ----

  // where hls.js plays it: with Media Source Extensions, and where the browser does not play HLS itself as well (and
  // list its tracks), or there are headers to send
  private def script: Boolean = WebMedia.scriptable && (headers.nonEmpty || !WebMedia.nativeHls(m))

  private def attach(to: String, at: Int): Unit = HlsScript.load { loaded =>
    if (at == opened) {
      if (!loaded) { if (WebMedia.nativeHls(m)) native(to) else fail("hls.js could not be loaded") }
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
      h.subtitleDisplay = i >= 0 && !WebMedia.drawsCues
    }
  }

  private def picked(kind: String): Option[Int] = pick.get(kind)

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
    keeping = false
    played = false
    loaded = false
    kept = None
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
    cues.clear()
    if (held) {
      m.pause()
      el.removeAttribute("src")
      val sources = el.querySelectorAll("source")
      (0 until sources.length).foreach(i => el.removeChild(sources(i)))
      m.load()
    }
  }

  // ---- moved ----

  /** It, or a box holding it, put somewhere on the page (`RenderNode.place`), with a source: its boxes marked again,
    * and it looked at at once and a moment after ([[MoveReset.checks]]), each look let go once another move, a seek or
    * a track chosen has come, or another source. */
  def moved(): Unit = if (url.nonEmpty) {
    path.foreach(_.mark())
    cues.place()
    moves += 1
    val move = moves
    val source = opened
    val was = known
    def look(): Unit = if (move == moves && source == opened && WebFeatures.connected(el)) after(was)
    look()
    MoveReset.checks.foreach(ms => dom.window.setTimeout(() => look(), ms))
  }

  // after a move: opened again where it was if the engine let the source go, else started again if it paused it
  private def after(was: Double): Unit = {
    val ready = m.readyState.asInstanceOf[Int]
    val network = m.networkState.asInstanceOf[Int]
    val now = m.currentTime.asInstanceOf[Double]
    if (MoveReset.dropped(loaded, was, ready, network, now, m.seeking.asInstanceOf[Boolean], m.loop.asInstanceOf[Boolean])) {
      everyui.scribe.warn("media: the source was let go as the element moved (ready " + ready + ", network " + network +
        ", at " + now.toInt + " s): opening it again at " + was.toInt + " s")
      restore(was)
    }
    else if (meantToPlay) {
      everyui.scribe.info("media: paused as the element moved: playing on at " + now.toInt + " s")
      resume()
    }
  }

  // the source opened again `at` seconds in, as it was: through hls.js where it played through it, with its tracks
  // (hls.js's choice, the element's own, the sidecar shown), the volume, muting and rate, playing or paused as asked
  private def restore(at: Double): Unit = url.foreach { to =>
    val sound = (m.volume.asInstanceOf[Double], m.muted.asInstanceOf[Boolean], m.playbackRate.asInstanceOf[Double])
    val chosen = hls.map(h => Map(MediaTrack.Audio -> h.audioTrack.asInstanceOf[Int],
      MediaTrack.Text -> h.subtitleTrack.asInstanceOf[Int]).filter(c => c._1 == MediaTrack.Text || c._2 >= 0))
    val own = (extra.ownHeard, extra.ownChosen)
    val was = played
    open(to, at, through = hls.nonEmpty)
    chosen.foreach(c => pick = c)
    extra.restoreOwn(own._1, own._2)
    played = was
    known = at
    put(sound)
    kept = Some(sound)
    restores += 1
    el.setAttribute("data-everyui-restores", restores.toString)
  }

  private def put(sound: (Double, Boolean, Double)): Unit = {
    m.volume = sound._1
    m.muted = sound._2
    m.playbackRate = sound._3
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
  /** Whether EveryUI draws the subtitles itself over the picture ([[WebCues]]) rather than leaving them to the browser
    * (`::cue`): None (the default) as [[CueDrawing]] says, on a TV's host (`AppHosts`: webOS, Tizen and Vega as they
    * come) and an engine before Chromium 62 (`TvEngine.squashesOverVideo`); Some(true) everywhere, Some(false) nowhere.
    * Heard at once by every player playing. */
  val drawCues: Var[Option[Boolean]] = Var(None)

  /** The look of the subtitles EveryUI draws: colours, edge and scale as the device's caption settings give them on
    * Android ([[everyui.text.CaptionStyle]]); None (the default) for white words edged in black on a dark box, 4.5% of
    * the video's height (`WebCues.look`). Heard at once. */
  val cueStyle: Var[Option[CaptionStyle]] = Var(None)

  /** Whether EveryUI draws the subtitles here, now ([[drawCues]]). */
  def drawsCues: Boolean = CueDrawing.drawn(drawCues(), AppHosts.current.nonEmpty, WebFeatures.videoSquashes)

  // the players holding a source, and hls.js's players (each with its MediaSource) not yet destroyed
  private[render] var holding: Set[WebMedia] = Set.empty
  private[render] var scripts: Int = 0
  drawCues.attach(_ => holding.toList.foreach(_.redrawn()))
  cueStyle.attach(_ => holding.toList.foreach(_.cued()))
  /** `child` put on the page (`RenderNode.place`): each player holding a source in it (it, or one inside it) told it
    * moved. */
  def placed(child: dom.Element): Unit =
    if (holding.nonEmpty && WebFeatures.connected(child))
      holding.toList.filter(w => (w.el eq child) || child.contains(w.el)).foreach(_.moved())

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

  /** Whether hls.js can play here: Media Source Extensions, and the built-ins hls.js 1.7 calls on (`WebFeatures.hlsJs`;
    * not on webOS 3's Chromium 38, where it throws as it loads). */
  def scriptable: Boolean = mse && WebFeatures.hlsJs
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
