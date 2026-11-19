package everyui.webcheck

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import fabric.*
import rapid.*

import java.net.{InetSocketAddress, URLEncoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration.*

/** The web's video player as a TV app uses it, in a Chrome of any age (today's, headless; Chromium 53, webOS 4's
  * engine, under `xvfb-run`), over local test media served by a server of its own: never a real account's.
  *
  * First the example's `demo=decodes`, its answers printed, and the ones every engine agrees on held to: the clip's
  * codec plays, DTS does not, every question answered. With `--webos`, the page has a stubbed `webOSSystem` and a Luna
  * bus whose configuration service says HDR10, Dolby Vision and Atmos, and those are held to as well.
  *
  * Then `demo=player rounds=<n>` (PlayerRounds): a player screen entered and left n times (20), each round opening the
  * source 20 s in with two sidecar subtitle files (WebVTT and SubRip), then, a step apart, the second sound track, each
  * text track in turn, none, a seek 10 s on, and leaving. Each round is checked:
  *
  *  - it opened where it was asked to (the playhead said at `ready` within 2 s of 20, and the element there: the
  *    resume);
  *  - the second sound track is the one heard (hls.js's choice, marked `*`), where there is more than one;
  *  - each text track chosen is the one the element shows (its `textTracks`, by label), and then none is;
  *  - the seek landed at 30 (or a little on, played since);
  *  - left, nothing is held: `MediaSupport.held` 0, at most one `<video>` on the page and none with a source, no
  *    sidecar `<track>`, and no MediaSource open (each the page makes counted by a stand-in for the constructor).
  *
  * At the end no `<video>` is left at all and no MediaSource is open, and the console has no uncaught error. Prints the
  * page's console and each check; exits 1 where one fails.
  *
  *   PlayerCheck <page dir> <media dir> [--chrome <binary>] [--webos] [--rounds <n>] [--src <path under media>]
  *
  * The page dir is the example's web build with hls.js beside it (`everyui-hls/<version>/`); the media dir holds
  * `hls/master.m3u8` (H.264 with two AAC renditions and two WebVTT subtitle renditions), `clip.webm` (VP8 and Opus),
  * `side-en.vtt` and `side-fr.srt`. The source is the HLS stream where the browser plays H.264, else the WebM. */
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

  override def run(args: List[String]): Task[Unit] = {
    val usage = "usage: PlayerCheck <page dir> <media dir> [--chrome <binary>] [--webos] [--rounds <n>] [--src <path>]"
    def option(name: String): Option[String] = args.dropWhile(_ != name).drop(1).headOption
    val page = Paths.get(args.headOption.getOrElse(sys.error(usage))).toAbsolutePath.normalize()
    val media = Paths.get(args.lift(1).getOrElse(sys.error(usage))).toAbsolutePath.normalize()
    val chrome = option("--chrome").getOrElse(DevTools.defaultChrome)
    val webos = args.contains("--webos")
    val rounds = option("--rounds").map(_.toInt).getOrElse(20)
    val server = serve(page, media)
    val origin = "http://127.0.0.1:" + server.getAddress.getPort
    var failed = List.empty[String]
    def check(what: String, ok: Boolean): Unit = {
      println((if (ok) "[player] ok " else "[player] FAILED ") + what)
      System.out.flush()
      if (!ok) failed :+= what
    }
    DevTools.around(chrome) { browser =>
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
      val held = "var vs = [].slice.call(document.querySelectorAll('video')); return [vs.length, " +
        "vs.filter(function (v) { return v.hasAttribute('src') || v.querySelector('source'); }).length, " +
        "document.querySelectorAll('track[id^=everyui-side-]').length, " +
        "window.everyuiSources.filter(function (s) { return s.readyState != 'closed'; }).length, " +
        "window.everyuiSources.length]"
      def number(text: String, after: String): Double =
        text.split(after).lift(1).flatMap(_.trim.split(' ').headOption).flatMap(_.toDoubleOption).getOrElse(-1.0)
      val h264 = value("return !!window.MediaSource && " +
        "MediaSource.isTypeSupported('video/mp4; codecs=\"avc1.640028\"')").map(_.asBoolean)
      for {
        _ <- browser.addScript(counting + (if (webos) "\n" + webOS else ""))
        playsH264 <- h264
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
        _ = if (webos) check("an LG TV's own word for HDR10, HLG, Dolby Vision and Atmos is taken (getConfigs)",
          answers.contains("hdr10 = true") && answers.contains("hlg = true") && answers.contains("dolbyvision = true") &&
            answers.contains("atmos = true")) else ()
        // ---- a player screen entered and left ----
        source = option("--src").getOrElse(if (playsH264) "hls/master.m3u8" else "clip.webm")
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
              else Task.pure(true)
            shown <- sequence(texts.map { t =>
              line(p + "text " + t(1) + " ").flatMap(_ => becomes(showing + ".join('|') == " + quote(t(3)), 2.0))
            })
            off <- line(p + "text off").flatMap(_ => becomes(showing + ".length == 0", 2.0))
            sought <- line(p + "sought ")
            leftLine <- line(p + "left held=")
            empty <- becomes("var h = (function () { " + held + " })(); " +
              "return h[0] <= 1 && h[1] == 0 && h[2] == 0 && h[3] == 0", 3.0)
            counts <- value(held)
          } yield {
            val ok = (resumed, heard, shown.forall(identity), off,
              sought.exists(s => number(s, "sought ") >= 29.0 && number(s, "sought ") <= 34.0),
              leftLine.exists(_.endsWith("held=0")),
              empty)
            println("[player] round " + n + ": tracks " + tracks.map(t => t(0) + ":" + t(3)).mkString(", ") +
              "; resumed " + ok._1 + ", sound " + ok._2 + ", text " + shown.mkString("/") + ", off " + ok._4 +
              ", seek " + ok._5 + ", let go " + ok._6 + ", page " + counts.toString)
            System.out.flush()
            (ok, texts.size, audio.size)
          }
        })
        done <- line("player rounds done held=")
        end <- becomes("var h = (function () { " + held + " })(); return h[0] == 0 && h[3] == 0", 5.0)
        made <- value(held)
      } yield {
        check("every round opened where it was asked to (20 s): " + results.count(_._1._1) + " of " + rounds,
          results.forall(_._1._1))
        if (results.exists(_._3 > 1)) check("the second sound track was heard each round", results.forall(_._1._2))
        check("each text track was shown as chosen, " + results.map(_._2).maxOption.getOrElse(0) + " a round",
          results.forall(r => r._1._3 && r._2 > 0))
        check("and then none", results.forall(_._1._4))
        check("each seek landed", results.forall(_._1._5))
        check("each round left with nothing held (MediaSupport.held 0, no source, track or open MediaSource; " +
          "one <video> at most)", results.forall(r => r._1._6 && r._1._7))
        check("at the end no <video> and no MediaSource open (" + made.asVector(4).asInt + " made in all)",
          done.exists(_.endsWith("held=0")) && end)
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
  private def serve(page: Path, media: Path): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
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
