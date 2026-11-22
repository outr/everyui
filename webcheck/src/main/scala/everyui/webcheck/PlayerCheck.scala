package everyui.webcheck

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import fabric.*
import rapid.*

import java.net.{InetSocketAddress, URLEncoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration.*

/** The web's video player as a TV app uses it, in a Chrome of any age (today's, headless; Chromium 53, webOS 4's
  * engine, and 38, webOS 3's, under `xvfb-run`), over local test media served by a server of its own: never a real
  * account's.
  *
  * First the example's `demo=decodes`, its answers printed, and the ones every engine agrees on held to: the clip's
  * codec plays, DTS does not, every question answered. With `--webos`, the page has a stubbed `webOSSystem` and a Luna
  * bus whose configuration service says HDR10, Dolby Vision and Atmos, and those are held to as well, with HDR10+ a no
  * (LG makes none).
  *
  * Then `demo=player rounds=<n>` (PlayerRounds): a player screen entered and left n times (20), each round opening the
  * source 20 s in with two sidecar subtitle files (WebVTT and SubRip), then, a step apart, the second sound track, each
  * text track in turn, none, a seek 10 s on, a step to land, and leaving. Each round is checked:
  *
  *  - it opened where it was asked to (the playhead said at `ready` within 2 s of 20, and the element there: the
  *    resume);
  *  - the second sound track is the one heard (hls.js's choice or the element's own, marked `*`), where there is more
  *    than one, and the element still where it was, not back at the start;
  *  - no text track shows before one is chosen;
  *  - each text track chosen is the one the element shows (its `textTracks`, by label), and then none is;
  *  - the seek landed at 30 (or a little on, played since), said and on the element;
  *  - moved into a mini player's box (made off the page, then put on it), it plays on in its new place from where it
  *    was, said and on the element, the sound track chosen still heard and no text shown; before Chromium 62 every
  *    box holding it up to the app's root is marked `everyui-video-path`, and no box is marked that holds no video
  *    with a source (`VideoPath`);
  *  - left, nothing is held: `MediaSupport.held` 0, at most one `<video>` on the page and none with a source, no
  *    sidecar `<track>`, and no MediaSource open (each the page makes counted by a stand-in for the constructor).
  *
  * At the end no `<video>` is left at all and no MediaSource is open, and the console has no uncaught error. Prints the
  * page's console and each check; exits 1 where one fails.
  *
  * `--tv` (with `--webos`) makes the element a TV's own player as webOS 3's is: it says it plays HLS, and plays the
  * playlist itself (where the engine plays no H.264, the WebM stands in for it); it lists the playlist's two sound
  * tracks as its own `audioTracks` and starts again from 0 half a second after another is chosen; it lists none of the
  * playlist's subtitles; it shows each subtitle `<track>` as it is added, as the TV's caption setting does; and a seek
  * to 30 springs back 4.4 s once, as an MP4's did. So the source is the playlist, its subtitles are read from their
  * WebVTT segments, and on Chromium 38 hls.js is never loaded. `--attach <inspector>` drives a browser already running
  * (a TV's, `http://<tv>:9998`) rather than starting one, its media served on `--host <address>` (the server's
  * address as the browser reaches it, 127.0.0.1 else); the stubs are not put in it.
  *
  * `--drop` stands in for webOS 3.5's Chromium 38, which lets a video's source go as the element is moved (nothing
  * loaded, no source, the playhead at 0): each loaded `<video>` put on the page has its source taken away and given
  * back, and each move must be heard opened again where it was (`MoveReset`). Without it, no move may open a source
  * again.
  *
  *   PlayerCheck <page dir> <media dir> [--chrome <binary> | --attach <inspector> --host <address>] [--webos [--tv]]
  *     [--rounds <n>] [--src <path under media>] [--drop]
  *
  * The page dir is the example's web build with hls.js beside it (`everyui-hls/<version>/`); the media dir holds
  * `hls/master.m3u8` (H.264 with two AAC renditions and two WebVTT subtitle renditions), `clip.webm` (VP8 and Opus),
  * `side-en.vtt` and `side-fr.srt`. The source is the HLS stream where the browser plays H.264 or with `--tv`, else the
  * WebM. */
object PlayerCheck extends RapidApp {
  // every MediaSource the page makes, kept to be counted, before the page's own scripts
  private val counting =
    """(function () {
      |  window.everyuiSources = [];
      |  var Made = window.MediaSource;
      |  if (!Made) return;
      |  var Counted = function () { var s = new Made(); window.everyuiSources.push(s); return s; };
      |  Counted.prototype = Made.prototype;
      |  Counted.isTypeSupported = function (t) { return Made.isTypeSupported(t); };
      |  window.MediaSource = Counted;
      |})();""".stripMargin

  // an LG TV's runtime, and a Luna bus whose configuration service says what the TV shows
  private val webOS =
    """window.webOSSystem = { launchParams: "{}", platformBack: function () {},
      |  deviceInfo: JSON.stringify({ modelName: "OLED55C1", platformVersionMajor: "6" }) };
      |window.everyuiLuna = [];
      |window.PalmServiceBridge = function () {
      |  var bridge = this;
      |  bridge.call = function (uri, params) {
      |    window.everyuiLuna.push(uri);
      |    var answer = uri == "luna://com.webos.service.config/getConfigs" ? { returnValue: true, configs: {
      |      "tv.model.supportHDR": true, "tv.config.supportDolbyHDRContents": true, "tv.hw.panelResolution": "UD",
      |      "tv.config.supportDolbyTVATMOS": true } } : { returnValue: false, errorText: "not stubbed" };
      |    setTimeout(function () { bridge.onservicecallback(JSON.stringify(answer)); }, 10);
      |  };
      |};""".stripMargin

  // a TV's own player, as webOS 3's is (ES 5.1): HLS its own (`everyuiStandIn` playing for the playlist where the engine
  // has no H.264), the playlist's sound tracks its own and the start again from 0 after one is chosen, each subtitle
  // <track> shown as it is added, and a seek to 30 sprung back once
  private val tv =
    """(function () {
      |  var P = HTMLMediaElement.prototype;
      |  var canPlay = P.canPlayType;
      |  P.canPlayType = function (t) { return /mpegurl/i.test(t) ? "maybe" : canPlay.call(this, t); };
      |  var set = Element.prototype.setAttribute;
      |  Element.prototype.setAttribute = function (n, v) {
      |    if (this instanceof HTMLMediaElement && n == "src" && window.everyuiStandIn && /\.m3u8/.test(v))
      |      v = window.everyuiStandIn;
      |    return set.call(this, n, v);
      |  };
      |  window.everyuiResets = 0;
      |  window.everyuiSprings = 0;
      |  window.everyuiShownUnasked = 0;
      |  Object.defineProperty(P, "audioTracks", { configurable: true, get: function () {
      |    var el = this;
      |    if (el.everyuiAudio) return el.everyuiAudio;
      |    var list = { length: 2 };
      |    [["en", "English"], ["fr", "Français"]].forEach(function (t, i) {
      |      var on = i == 0;
      |      list[i] = { id: String(i), kind: i == 0 ? "main" : "alternative", language: t[0], label: t[1] };
      |      Object.defineProperty(list[i], "enabled", { get: function () { return on; }, set: function (v) {
      |        if (!!v == on) return;
      |        on = !!v;
      |        if (on) setTimeout(function () {
      |          if (el.hasAttribute("src")) { window.everyuiResets++; el.currentTime = 0; }
      |        }, 500);
      |      } });
      |    });
      |    el.everyuiAudio = list;
      |    return list;
      |  } });
      |  var append = Node.prototype.appendChild;
      |  Node.prototype.appendChild = function (child) {
      |    var made = append.call(this, child);
      |    if (this instanceof HTMLMediaElement && child.tagName == "TRACK") setTimeout(function () {
      |      if (child.track && child.track.mode != "showing") { window.everyuiShownUnasked++; child.track.mode = "showing"; }
      |    }, 0);
      |    return made;
      |  };
      |  document.addEventListener("seeked", function (e) {
      |    var v = e.target;
      |    if (v.currentTime > 29 && v.currentTime < 31 && !v.everyuiSprang) {
      |      v.everyuiSprang = true;
      |      setTimeout(function () { window.everyuiSprings++; v.currentTime = v.currentTime - 4.4; }, 300);
      |    }
      |  }, true);
      |})();""".stripMargin

  // webOS 3.5's Chromium 38 as a move finds it (`--drop`): a loaded video put on the page (itself, or in a box) has its
  // source taken away and given back, which leaves it with nothing loaded and its playhead at 0
  private val drop =
    """(function () {
      |  window.everyuiDrops = 0;
      |  var set = Element.prototype.setAttribute;
      |  function dropped(node) {
      |    var all = node.tagName == "VIDEO" ? [node] : node.querySelectorAll ? [].slice.call(node.querySelectorAll("video")) : [];
      |    all.forEach(function (v) {
      |      if (!document.contains(v) || v.readyState == 0 || !v.hasAttribute("src")) return;
      |      var src = v.getAttribute("src");
      |      window.everyuiDrops++;
      |      v.removeAttribute("src");
      |      v.load();
      |      set.call(v, "src", src);
      |    });
      |  }
      |  ["appendChild", "insertBefore"].forEach(function (name) {
      |    var put = Node.prototype[name];
      |    Node.prototype[name] = function (child) {
      |      var made = put.apply(this, arguments);
      |      dropped(child);
      |      return made;
      |    };
      |  });
      |})();""".stripMargin

  override def run(args: List[String]): Task[Unit] = {
    val usage = "usage: PlayerCheck <page dir> <media dir> [--chrome <binary> | --attach <inspector> --host <address>] " +
      "[--webos [--tv]] [--rounds <n>] [--src <path>] [--drop]"
    def option(name: String): Option[String] = args.dropWhile(_ != name).drop(1).headOption
    val page = Paths.get(args.headOption.getOrElse(sys.error(usage))).toAbsolutePath.normalize()
    val media = Paths.get(args.lift(1).getOrElse(sys.error(usage))).toAbsolutePath.normalize()
    val chrome = option("--chrome").getOrElse(DevTools.defaultChrome)
    val attach = option("--attach")
    val host = option("--host").getOrElse("127.0.0.1")
    val tvPlayer = args.contains("--tv") && attach.isEmpty
    val webos = args.contains("--webos") || tvPlayer
    val rounds = option("--rounds").map(_.toInt).getOrElse(20)
    val dropping = args.contains("--drop") && attach.isEmpty
    val server = serve(page, media, host)
    val origin = "http://" + host + ":" + server.getAddress.getPort
    def browsing[A](f: DevTools => Task[A]): Task[A] = attach match {
      case Some(address) => DevTools.attached(address)(f)
      case None => DevTools.around(chrome)(f)
    }
    var failed = List.empty[String]
    def check(what: String, ok: Boolean): Unit = {
      println((if (ok) "[player] ok " else "[player] FAILED ") + what)
      System.out.flush()
      if (!ok) failed :+= what
    }
    browsing { browser =>
      val lines = new java.util.concurrent.CopyOnWriteArrayList[String]()
      browser.onLine { line =>
        lines.add(line)
        println(line)
        System.out.flush()
      }
      def all: List[String] = lines.toArray.toList.map(_.toString)
      // the first line containing `part`, within `seconds`
      def line(part: String, seconds: Int = 30): Task[Option[String]] = {
        def look(left: Int): Task[Option[String]] = all.find(_.contains(part)) match {
          case found @ Some(_) => Task.pure(found)
          case None => if (left == 0) Task.pure(None) else Task.sleep(100.millis).flatMap(_ => look(left - 1))
        }
        look(seconds * 10)
      }
      def value(js: String): Task[Json] = browser.value(js)
      // `js` (a boolean) true within `seconds`
      def becomes(js: String, seconds: Double): Task[Boolean] = {
        def look(left: Int): Task[Boolean] = value(js).flatMap { v =>
          if (v.isBool && v.asBoolean) Task.pure(true)
          else if (left == 0) Task.pure(false)
          else Task.sleep(100.millis).flatMap(_ => look(left - 1))
        }
        look((seconds * 10).toInt)
      }
      // (ES 5.1 for Chromium 38: no arrows, no const, no Array.flatMap before Chromium 69)
      val showing = "return [].concat.apply([], [].slice.call(document.querySelectorAll('video'))" +
        ".map(function (v) { return [].slice.call(v.textTracks); }))" +
        ".filter(function (t) { return t.mode == 'showing'; })" +
        ".map(function (t) { return t.label; })"
      // where the element is, -1 with none
      val at = "(function () { var v = document.querySelector('video'); return v ? v.currentTime : -1; })()"
      val held = "var vs = [].slice.call(document.querySelectorAll('video')); return [vs.length, " +
        "vs.filter(function (v) { return v.hasAttribute('src') || v.querySelector('source'); }).length, " +
        "document.querySelectorAll('track[id^=everyui-side-]').length, " +
        "(window.everyuiSources || []).filter(function (s) { return s.readyState != 'closed'; }).length, " +
        "(window.everyuiSources || []).length]"
      // before Chromium 62, every box from the video up to the app's root marked, and none marked holding no video with
      // a source; elsewhere nothing to mark
      val marks = "var m = /Chrome\\/(\\d+)/.exec(navigator.userAgent); if (!m || +m[1] > 61) return 'unmarked engine'; " +
        "var v = document.querySelector('video'), missing = 0; " +
        "for (var b = v.parentNode; b && b.id != 'app'; b = b.parentNode) if (!b.classList.contains('everyui-video-path')) missing++; " +
        "var stale = [].slice.call(document.querySelectorAll('.everyui-video-path'))" +
        ".filter(function (b) { return !b.querySelector('video[src]'); }).length; " +
        "return missing == 0 && stale == 0 ? 'marked' : 'missing ' + missing + ' stale ' + stale"
      def number(text: String, after: String): Double =
        text.split(after).lift(1).flatMap(_.trim.split(' ').headOption).flatMap(_.toDoubleOption).getOrElse(-1.0)
      val h264 = value("return !!window.MediaSource && " +
        "MediaSource.isTypeSupported('video/mp4; codecs=\"avc1.640028\"')").map(_.asBoolean)
      for {
        playsH264 <- h264
        _ <- if (attach.nonEmpty) Task.unit else browser.addScript(counting + (if (webos) "\n" + webOS else "") +
          (if (tvPlayer) "\n" + tv + (if (playsH264) "" else "\nwindow.everyuiStandIn = '/media/clip.webm';") else "") +
          (if (dropping) "\n" + drop else ""))
        // ---- what it decodes ----
        _ <- browser.navigate(origin + "/app/index.html?demo=decodes")
        last <- line("decodes: atmos = ")
        answers = all.filter(_.contains("decodes: ")).map(_.split("decodes: ").last)
        _ = check("every decodes question is answered (" + answers.size + ")", last.nonEmpty && answers.size >= 50)
        _ = check("the clip's codecs play: VP8 and Opus in WebM",
          answers.contains("vp8 webm = true") && answers.contains("opus webm = true"))
        _ = check("DTS does not play in a browser", answers.contains("dtsc mp4 = false") && answers.contains("dts = false"))
        _ = if (playsH264) check("H.264 High and AAC play where the engine has them",
          answers.contains("avc1.640028 mp4 = true") && answers.contains("mp4a.40.2 mp4 = true")) else ()
        _ = if (webos) check("an LG TV's own word for HDR10, HLG, Dolby Vision and Atmos is taken (getConfigs), HDR10+ a no",
          answers.contains("hdr10 = true") && answers.contains("hlg = true") && answers.contains("dolbyvision = true") &&
            answers.contains("atmos = true") && answers.contains("hdr10plus = false")) else ()
        // ---- a player screen entered and left ----
        source = option("--src").getOrElse(if (playsH264 || tvPlayer) "hls/master.m3u8" else "clip.webm")
        query = "demo=player&rounds=" + rounds + "&start=20&step=1.2&src=" + enc("/media/" + source) + "&side=" +
          enc("/media/side-en.vtt|Sidecar|en,/media/side-fr.srt|SRT fr|fr")
        _ = lines.clear()
        _ <- browser.navigate(origin + "/app/index.html?" + query)
        results <- sequence((1 to rounds).toList.map { n =>
          val p = "player round " + n + " "
          for {
            ready <- line(p + "ready at ", 60)
            tracks = ready.map(_.split("tracks=").lift(1).getOrElse("")).getOrElse("").split('|').toList
              .filter(_.nonEmpty).map(_.split(":", -1).toList)
            // said at 20, and the element itself there or played on from it (not from the start)
            there <- becomes("var v = document.querySelector('video'); return !!v && v.currentTime >= 18 && " +
              "v.currentTime < 26", 3.0)
            resumed = there && ready.exists(r => math.abs(number(r, "ready at ") - 20.0) <= 2.0)
            audio = tracks.filter(_.head == "audio")
            texts = tracks.filter(_.head == "text")
            heard <- if (audio.size > 1) line(p + "audio ").map(_.exists(l => l.contains(audio(1)(2) + "*")))
              else Task.sleep(1200.millis).map(_ => true)
            // the place kept through the sound track's change (a TV's player starts again from 0 after it)
            kept <- becomes("return " + at + " >= 18 && " + at + " < 34", 3.0)
            // and nothing shown that was not chosen (a TV shows a track as its caption setting says)
            unasked <- value(showing + ".length == 0").map(_.asBoolean)
            shown <- sequence(texts.map { t =>
              line(p + "text " + t(1) + " ").flatMap(_ => becomes(showing + ".join('|') == " + quote(t(3)), 2.0))
                .flatMap { ok =>
                  if (ok) Task.pure(true) else value(showing + ".join('|')").map { v =>
                    println("[player] round " + n + ": " + t(3) + " chosen, showing \"" + v.asString + "\"")
                    false
                  }
                }
            })
            off <- line(p + "text off").flatMap(_ => becomes(showing + ".length == 0", 2.0))
            sought <- line(p + "sought ")
            // on the element too, at its segment's start or on from there (a TV's player sprang back once)
            landed <- becomes("return " + at + " >= 28 && " + at + " < 40", 1.0)
            movedLine <- line(p + "moved at ")
            was = movedLine.map(number(_, "moved at ")).getOrElse(-1.0)
            // in the box, playing on from where it was (a little on, played since)
            playsOn <- becomes("var v = document.querySelector('video'); return !!v && v.parentNode.offsetWidth == 480 && " +
              "!v.paused && v.readyState >= 2 && v.currentTime >= " + (was - 2.0) + " && v.currentTime < " + (was + 8.0), 4.0)
            afterLine <- line(p + "after move ")
            said = afterLine.exists(l => number(l, "after move ") >= was - 2.0 && number(l, "after move ") < was + 8.0)
            // with the sound track chosen still heard, and still no text shown
            still <- value(showing + ".length == 0").map(_.asBoolean)
            chosenKept = still && (audio.size < 2 || afterLine.exists(_.contains(audio(1)(2) + "*")))
            marked <- value(marks).map(_.asString)
            leftLine <- line(p + "left held=")
            empty <- becomes("var h = (function () { " + held + " })(); " +
              "return h[0] <= 1 && h[1] == 0 && h[2] == 0 && h[3] == 0", 3.0)
            counts <- value(held)
          } yield {
            val ok = (resumed, heard && kept, shown.forall(identity), off,
              landed && sought.exists(s => number(s, "sought ") >= 29.0 && number(s, "sought ") <= 34.0),
              leftLine.exists(_.endsWith("held=0")),
              empty, unasked, playsOn && said && chosenKept && marked != "" && !marked.startsWith("missing"))
            println("[player] round " + n + ": tracks " + tracks.map(t => t(0) + ":" + t(3)).mkString(", ") +
              "; resumed " + ok._1 + ", sound " + heard + " kept " + kept + ", unasked none " + unasked + ", text " +
              shown.mkString("/") + ", off " + ok._4 + ", seek " + ok._5 + ", moved at " + was + " plays on " + playsOn +
              " said " + said + " tracks kept " + chosenKept + " " + marked + ", let go " + ok._6 + ", page " + counts.toString)
            System.out.flush()
            (ok, texts.size, audio.size)
          }
        })
        done <- line("player rounds done held=")
        end <- becomes("var h = (function () { " + held + " })(); return h[0] == 0 && h[3] == 0", 5.0)
        made <- value(held)
        stubbed <- value("return [window.everyuiResets, window.everyuiSprings, window.everyuiShownUnasked]")
        drops <- value("return window.everyuiDrops || 0")
      } yield {
        check("every round opened where it was asked to (20 s): " + results.count(_._1._1) + " of " + rounds,
          results.forall(_._1._1))
        if (results.exists(_._3 > 1)) check("the second sound track was heard each round, the place kept",
          results.forall(_._1._2))
        check("no text track showed before one was chosen", results.forall(_._1._8))
        check("each text track was shown as chosen, " + results.map(_._2).maxOption.getOrElse(0) + " a round",
          results.forall(r => r._1._3 && r._2 > 0))
        check("and then none", results.forall(_._1._4))
        check("each seek landed", results.forall(_._1._5))
        check("each move into another box played on from where it was, its tracks as chosen, its boxes marked where they are to be",
          results.forall(_._1._9))
        val reopened = all.count(_.contains("media: the source was let go as the element moved"))
        if (dropping) check("each move's source let go (" + drops.asInt + ") was opened again where it was (" + reopened +
          ")", drops.asInt >= rounds && reopened >= rounds)
        else check("no move opened a source again (" + reopened + ")", reopened == 0)
        check("each round left with nothing held (MediaSupport.held 0, no source, track or open MediaSource; " +
          "one <video> at most)", results.forall(r => r._1._6 && r._1._7))
        check("at the end no <video> and no MediaSource open (" + made.asVector(4).asInt + " made in all)",
          done.exists(_.endsWith("held=0")) && end)
        if (tvPlayer) {
          val s = stubbed.asVector.map(v => if (v.isNumInt || v.isNumDec) v.asInt else 0)
          check("the TV's player started again from 0 after each sound track (" + s(0) + "), sprang back from each " +
            "seek (" + s(1) + ") and showed tracks unasked (" + s(2) + ")", s(0) >= rounds && s(1) >= rounds && s(2) > 0)
        }
        val errors = all.filter(l => l.contains("[uncaught]") || l.toLowerCase.contains("uncaught"))
        check("no uncaught errors on the console", errors.isEmpty)
      }
    }.guarantee(Task(server.stop(0))).map { _ =>
      println(if (failed.isEmpty) "[player] all passed" else "[player] failed: " + failed.mkString("; "))
      if (failed.nonEmpty) sys.exit(1)
    }
  }

  // one after another, in order
  private def sequence[A](all: List[Task[A]]): Task[List[A]] =
    all.foldLeft(Task.pure(List.empty[A]))((done, next) => done.flatMap(a => next.map(b => a :+ b)))

  private def enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)
  private def quote(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

  // the page under /app/ and the media under /media/, with byte ranges, as a TV's player and a browser seek by them
  private def serve(page: Path, media: Path, host: String): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(host, 0), 0)
    def files(root: Path)(x: HttpExchange): Unit = try {
      val prefix = x.getHttpContext.getPath
      val rel = java.net.URLDecoder.decode(x.getRequestURI.getPath.stripPrefix(prefix).stripPrefix("/"), "UTF-8")
      val file = root.resolve(rel).normalize()
      if (!file.startsWith(root) || !Files.isRegularFile(file)) {
        x.sendResponseHeaders(404, -1)
      }
      else {
        val bytes = Files.readAllBytes(file)
        val name = file.getFileName.toString
        val kind = Map("html" -> "text/html", "js" -> "text/javascript", "m3u8" -> "application/vnd.apple.mpegurl",
          "ts" -> "video/mp2t", "aac" -> "audio/aac", "vtt" -> "text/vtt", "srt" -> "text/plain", "webm" -> "video/webm",
          "ttf" -> "font/ttf", "png" -> "image/png", "svg" -> "image/svg+xml", "json" -> "application/json")
          .getOrElse(name.split('.').last, "application/octet-stream")
        val headers = x.getResponseHeaders
        headers.set("Content-Type", kind)
        headers.set("Accept-Ranges", "bytes")
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Cache-Control", "no-store")
        val range = Option(x.getRequestHeaders.getFirst("Range")).filter(_.startsWith("bytes="))
          .map(_.stripPrefix("bytes=").split("-", -1))
        range match {
          case Some(Array(a, b)) if a.nonEmpty =>
            val from = a.toLong
            val to = if (b.isEmpty) bytes.length - 1L else math.min(b.toLong, bytes.length - 1L)
            headers.set("Content-Range", "bytes " + from + "-" + to + "/" + bytes.length)
            val length = (to - from + 1).toInt
            x.sendResponseHeaders(206, length.toLong)
            x.getResponseBody.write(bytes, from.toInt, length)
          case _ =>
            x.sendResponseHeaders(200, bytes.length.toLong)
            x.getResponseBody.write(bytes)
        }
      }
    }
    catch { case _: java.io.IOException => () }
    finally x.close()
    server.createContext("/app", x => files(page)(x))
    server.createContext("/media", x => files(media)(x))
    // daemon threads, so the check ends with its browser
    server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8, (r: Runnable) => {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    }))
    server.start()
    server
  }
}
