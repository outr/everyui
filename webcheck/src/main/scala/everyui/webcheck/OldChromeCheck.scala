package everyui.webcheck

import fabric.*
import rapid.*

import com.sun.net.httpserver.HttpServer

import java.net.{InetAddress, InetSocketAddress}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

/** The example app's demos in an old TV's engine (Chromium 38, webOS 3's; 53, webOS 4's; 56, Tizen 4's), or any
  * Chrome: each loaded from its files at 1920 by 1080, given a few of the remote's keys and the pointer's moves and
  * clicks, and checked to have drawn its screen and left no error uncaught. `--shots` keeps a picture of each, for a
  * look at what it drew. Prints the page's console and each check; exits 1 where one fails. With WebOSCheck (its
  * `--chrome`), the check that a change still runs on the oldest TV EveryUI supports (Chromium 38, on the app linked
  * as ES 5.1). What it runs in the page is ES 5.1 too.
  *
  * `net` is the net demo making a request through Spice's client (`Network.http`) to a server of the check's own, on
  * 127.0.0.1: answered, and heard by the server. An HTTP request is where a regex needing ECMAScript 2018 (a
  * look-behind, a named group, `\p{..}`, dotAll) in what a request runs through fails an ES 5.1 link, as Spice's
  * URL parsing did before 1.10.13: every request on webOS 3 threw "Look-behind group is not supported". The server
  * answers a JSON object, which the demo reads with fabric and says back: fabric before 1.30.5 named the BigInt global
  * to tell a parsed value's kind, a ReferenceError for every object on an engine without it (Chromium 38).
  *
  *   OldChromeCheck <page dir> [--chrome <binary>] [--shots <dir>] [--video <file>] [demo ...]
  *
  * `corners` is railperf's rounded cards with posters at the size drawn, in a browser drawing as a TV does (its GPU on,
  * where it has no headless mode): each poster cut to its card's rounded corner, read off a picture of the page (before
  * Chromium 90, `overflow: hidden` pinned, `BoxClip`), a box so cut not shifted as the focus goes into it, and the
  * frames of a few moves said (a report, not a check).
  *
  * `playerbar` is a TV player's overlay over a playing video, nested as NaboTV's is (the example's `demo=playerbar`, its
  * video `--video`, `assets/media/bars.webm` from where the check runs unless named), in a browser drawing as a TV does
  * (before Chromium 62 under webOS 3.5's user agent): its rounded bar cut to its box, of a gradient seen through, an edge
  * and round buttons, drawn over the picture, read off a picture of the page with the bar and one with it hidden, which
  * must differ across the bar's box (before Chromium 62 a box over a video was put in a layer shared with others and
  * drawn under the picture or not at all, and a colour seen through, written `#rrggbbaa`, was not drawn:
  * `WebFeatures.videoSquashes`, `alphaHex`); what is opened over the playing picture drawn over it as the bar is, each
  * measured so: a menu dropped from the bar (`open=menu`, on the app's overlay layer), a card on the player's own screen
  * (`open=card`, as NaboTV's sound menu is) and a toast (`open=toast`); and, in an engine that says it reads `#rrggbbaa`
  * as webOS 3.5's Chromium 38 does, the over-video rule on the page before Chromium 62 and off after.
  *
  * `motionstop` is the demo of motions taken away mid-cycle: each node read from the engine's computed style to be
  * drawn as it has itself, not where its motion stood (a turn, a scale, a fade, a slide, a turn replaced by a fade, and a
  * busy Sync glyph turned into a done tick).
  *
  * The demos are tv, chat, rows, widgets, icons, net, motionstop, corners and playerbar unless named. An engine older
  * than 59 opens a window on `$DISPLAY`: run it under `xvfb-run`.
  */
object OldChromeCheck extends RapidApp {
  private val usage = "usage: OldChromeCheck <page dir> [--chrome <binary>] [--shots <dir>] [--video <file>] [demo ...]"

  override def run(args: List[String]): Task[Unit] = {
    def option(name: String): Option[String] = args.dropWhile(_ != name).drop(1).headOption
    val chrome = option("--chrome").getOrElse(DevTools.defaultChrome)
    // the player's page before Chromium 62 under the user agent of webOS 3.5's own Chromium 38 (an LG E7P's)
    val playerAgent = Option.when(DevTools.majorOf(chrome) < 62)("Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/38.0.2125.122 Safari/537.36 WebAppManager")
    val shots = option("--shots").map(Paths.get(_))
    // the demos named: what is neither an option nor its value
    def named(rest: List[String]): List[String] = rest match {
      case o :: _ :: more if o.startsWith("--") => named(more)
      case d :: more => d :: named(more)
      case Nil => Nil
    }
    val demos = named(args.drop(1)) match {
      case Nil => List("tv", "chat", "rows", "widgets", "icons", "net", "motionstop", "corners", "playerbar")
      case some => some
    }
    val page = Paths.get(args.headOption.getOrElse(sys.error(usage))).toAbsolutePath.resolve("index.html").toUri.toString
    shots.foreach(Files.createDirectories(_))
    var failed = List.empty[String]
    def check(what: String, ok: Boolean): Unit = {
      println((if (ok) "[old chrome] ok " else "[old chrome] FAILED ") + what)
      System.out.flush()
      if (!ok) failed :+= what
    }
    // A rail of rounded cards, each a poster at the size drawn (railperf, `posters=drawn`), in a browser drawing as a
    // TV does (its GPU on, where it has no headless mode): a card's poster cut to its rounded corner (a pixel just
    // inside its box at the corner is the page behind, where the edge's middle is the poster), a box cut so shifted by
    // nothing the browser does as the focus goes into it, and the frames of a few moves said, a report, not a check.
    def corners(browser: DevTools): Task[Unit] = {
      val before = browser.lines.size
      def since: List[String] = browser.lines.toArray.drop(before).map(_.toString).toList
      def key(code: Int, name: String): Task[Unit] = for {
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("rawKeyDown"),
          "windowsVirtualKeyCode" -> num(code), "key" -> str(name)))
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("keyUp"),
          "windowsVirtualKeyCode" -> num(code), "key" -> str(name)))
        _ <- Task.sleep(700.millis)
      } yield ()
      // the second card of the first rail (the first has the focus ring), its box on the page
      val card = "var all = document.querySelectorAll('#app img'), card = null; for (var i = 0; i < all.length; i++) " +
        "if (/card=1$/.test(all[i].getAttribute('src') || '') && all[i].complete) card = all[i].parentNode; "
      browser.onLine { line =>
        println(line)
        System.out.flush()
      }
      for {
        _ <- browser.addScript("Error.stackTraceLimit = 50")
        _ <- browser.size(1920, 1080)
        _ <- browser.navigate(page + "?demo=railperf&rails=3&n=12&posters=drawn")
        _ <- Task.sleep(4.seconds)
        box <- browser.value(card + "if (!card) return null; var r = card.getBoundingClientRect(); " +
          "return [r.left, r.top, r.width, r.height, innerWidth, getComputedStyle(card).overflow]")
        shot <- browser.picture()
        _ <- shots.map(d => browser.screenshot(d.resolve("corners.png"))).getOrElse(Task.unit)
        _ = {
          val found = box.isArr
          check("corners: a card with its poster drawn", found)
          if (found) {
            val b = box.asVector
            val (left, top, width, across) = (b(0).asDouble, b(1).asDouble, b(2).asDouble, b(4).asDouble)
            // the picture's pixels to a CSS pixel as it was taken (an engine with no headless mode takes its window's)
            val k = shot.getWidth / across
            def green(x: Double, y: Double): Int = (shot.getRGB((x * k).toInt, (y * k).toInt) >> 8) & 0xff
            // 2.5 CSS pixels in from both edges is well outside a corner rounded 20 across (the card's 10 at scale 2)
            val corner = green(left + 2.5, top + 2.5)
            val edge = green(left + width / 2.0, top + 3.0)
            check("corners: the poster cut to the card's rounded corner (" + b(5).asString + "; green " + corner +
              " at the corner, the page's, " + edge + " on the edge, the poster's)", corner < 45 && edge > 60)
          }
        }
        // something to focus out of the card's box, as a row of a panel lower in it is (still in the rails' view, so
        // they do not move): the browser scrolls a box that can scroll (`hidden`) to show it, which the box puts back;
        // one cut by `clip` it cannot scroll at all
        _ <- browser.value(card + "var d = document.createElement('div'); d.id = 'everyui-check-low'; " +
          "d.tabIndex = -1; d.style.cssText = 'position:absolute;left:0;top:200px;width:10px;height:10px'; " +
          "card.appendChild(d); " +
          "window.__cardAt = card.firstChild.getBoundingClientRect().top; d.focus(); " +
          "window.__shifted = card.scrollTop; return 0")
        _ <- Task.sleep(500.millis)
        held <- browser.value(card + "var low = document.getElementById('everyui-check-low'); " +
          "var at = card.firstChild.getBoundingClientRect().top; card.removeChild(low); " +
          "return [window.__shifted, card.scrollTop, card.scrollLeft, at - window.__cardAt]")
        _ = {
          val h = held.asVector.map(_.asDouble)
          val (shifted, scrollTop, scrollLeft, moved) = (h(0), h(1), h(2), h(3))
          check("corners: a cut box shifted by nothing as the focus goes into it (the browser moved it " + shifted +
            ", put back to " + scrollTop + ", " + scrollLeft + "; its poster moved " + moved + ")",
            scrollTop == 0.0 && scrollLeft == 0.0 && moved == 0.0)
        }
        frames = since.size
        _ <- key(40, "ArrowDown")
        _ <- List.fill(3)(key(39, "ArrowRight")).foldLeft(Task.unit)((a, b) => a.flatMap(_ => b))
        _ <- key(38, "ArrowUp")
        slow = since.drop(frames).filter(_.contains("railperf frame")).map(_.replaceAll(".*railperf frame ", ""))
        _ = println("[old chrome] railperf with posters at the size drawn, 5 moves: " + slow.size +
          " frames of 50 ms or more" + (if (slow.isEmpty) "" else " (" + slow.mkString(", ") + ")") +
          ", a report rather than a check")
        errors = since.filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR"))
        _ = check("corners: no error left uncaught" + errors.headOption.map(": " + _).getOrElse(""), errors.isEmpty)
      } yield ()
    }
    // A TV player's overlay over a playing video (`demo=playerbar`, nested as NaboTV's player is), in a browser drawing
    // as a TV does, before Chromium 62 under LG's user agent for webOS 3.5: its rounded bar drawn over the picture, the
    // page's picture with the bar and with it hidden differing across the bar's box (each pixel's largest channel
    // difference, averaged), and so what is opened over the picture once it plays: a menu dropped from the bar (on the
    // app's overlay layer, `Modal`, the focus moved in it by a key as a remote's is), a card put up on the player's own
    // screen after its layers (as NaboTV's sound menu is) and a toast; and no error left uncaught. Then the page again
    // in an engine that says it reads `#rrggbbaa`, as webOS 3.5's Chromium 38 does (LG's, not upstream's): the
    // over-video rule on the page and a box on the bar's way up to the app's root given its transform, before Chromium 62
    // and not after (`WebFeatures.videoSquashes`, which 64cb2792 asked of `#rrggbbaa` and so left off on the TV).
    def playerbar(browser: DevTools): Task[Unit] = {
      val before = browser.lines.size
      def since: List[String] = browser.lines.toArray.drop(before).map(_.toString).toList
      def heard(what: String, from: Int, tries: Int): Task[Unit] =
        if (tries == 0 || browser.lines.toArray.drop(from).exists(_.toString.contains(what))) Task.unit
        else Task.sleep(100.millis).flatMap(_ => heard(what, from, tries - 1))
      val video = Paths.get(option("--video").getOrElse("assets/media/bars.webm")).toAbsolutePath
      val bar = "var el = document.querySelector('[aria-label=\"player bar\"]'); "
      val url = page + "?demo=playerbar&src=" + video.toUri.toString
      // whether the page has the over-video rule, and how many boxes on the bar's way up to the app's root (the bar's
      // own and the root's not counted) are given a transform
      val layered = bar + "if (!el) return null; var rule = false; " +
        "for (var i = 0; i < document.styleSheets.length; i++) { var rs = document.styleSheets[i].cssRules || []; " +
        "for (var j = 0; j < rs.length; j++) if ((rs[j].cssText || '').indexOf('everyui-video-path') >= 0) rule = true; } " +
        "var n = 0; for (var e = el.parentElement; e && e.id !== 'app'; e = e.parentElement) " +
        "if (getComputedStyle(e).transform !== 'none') n++; return [rule, n]"
      // what each `open=` puts up, found as `el`: the toast's plate is the box on the app's root holding its words
      val opened = List(
        "menu" -> "var el = document.querySelector('[aria-label=\"player menu\"]'); ",
        "card" -> "var el = document.querySelector('[aria-label=\"player card\"]'); ",
        "toast" -> ("var el = null, all = document.querySelectorAll('#app *'); for (var i = 0; i < all.length; i++) " +
          "if (all[i].textContent === 'Audio: English (5.1)') el = all[i]; " +
          "while (el && el.parentElement && el.parentElement.id !== 'app') el = el.parentElement; "))
      val squashes = browser.major < 62
      // how far apart the page's pictures with `el` shown and hidden are across its box, a little inside its edges, or
      // None where it is not laid out
      def apart(find: String, name: String): Task[Option[Double]] = for {
        box <- browser.value(find + "if (!el) return null; var r = el.getBoundingClientRect(); " +
          "return [r.left, r.top, r.width, r.height, innerWidth]")
        shown <- browser.picture()
        _ <- shots.map(d => browser.screenshot(d.resolve(name + ".png"))).getOrElse(Task.unit)
        _ <- browser.value(find + "if (el) el.style.visibility = 'hidden'; return 0")
        _ <- Task.sleep(700.millis)
        hidden <- browser.picture()
        _ <- shots.map(d => browser.screenshot(d.resolve(name + "-hidden.png"))).getOrElse(Task.unit)
      } yield Option.when(box.isArr) {
        val b = box.asVector.map(_.asDouble)
        // the picture's pixels to a CSS pixel as it was taken
        val k = shown.getWidth / b(4)
        val (x0, y0) = ((b(0) * k).toInt + 4, (b(1) * k).toInt + 4)
        val (x1, y1) = (math.min(shown.getWidth, ((b(0) + b(2)) * k).toInt - 4),
          math.min(shown.getHeight, ((b(1) + b(3)) * k).toInt - 4))
        val each = for (x <- x0 until x1 by 3; y <- y0 until y1 by 3) yield {
          val (p, q) = (shown.getRGB(x, y), hidden.getRGB(x, y))
          List(16, 8, 0).map(s => math.abs(((p >> s) & 0xff) - ((q >> s) & 0xff))).max
        }
        if (each.isEmpty) 0.0 else each.sum.toDouble / each.size
      }
      def drawn(what: String, mean: Option[Double]): Unit = mean match {
        case None => check("playerbar: its " + what + " laid out", false)
        case Some(m) => check("playerbar: its " + what + " drawn over the picture (" + (math.round(m * 10.0) / 10.0) +
          " apart on average across its box, shown and hidden)", m >= 30.0)
      }
      // `open=` put up over the playing picture (a menu with the focus moved in it by a key, as a remote moves it)
      def over(name: String, find: String): Task[Option[Double]] = {
        val from = browser.lines.size
        for {
          _ <- browser.navigate(url + "&open=" + name)
          _ <- heard("playerbar opened " + name, from, 100)
          _ <- Task.sleep(1500.millis)
          _ <- if (name != "menu") Task.unit else for {
            _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("rawKeyDown"),
              "windowsVirtualKeyCode" -> num(40), "key" -> str("ArrowDown")))
            _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("keyUp"),
              "windowsVirtualKeyCode" -> num(40), "key" -> str("ArrowDown")))
            _ <- Task.sleep(700.millis)
          } yield ()
          mean <- apart(find, "playerbar-" + name)
        } yield mean
      }
      browser.onLine { line =>
        println(line)
        System.out.flush()
      }
      if (!Files.exists(video)) Task(check("playerbar: its video at " + video, false))
      else for {
        _ <- browser.addScript("Error.stackTraceLimit = 50")
        _ <- browser.size(1920, 1080)
        _ <- browser.navigate(url)
        _ <- heard("playerbar ready", before, 100)
        _ <- Task.sleep(1500.millis)
        barApart <- apart(bar, "playerbar")
        overs <- opened.foldLeft(Task(List.empty[(String, Option[Double])])) { case (done, (name, find)) =>
          done.flatMap(d => over(name, find).map(m => d :+ (name -> m)))
        }
        // the engine says it reads `#rrggbbaa` from here on, as webOS 3.5's does (Chromium 38 then draws no colour seen
        // through, which it does not read: nothing is measured after this)
        _ <- browser.addScript("(function () { var s = CSS.supports; CSS.supports = function (p, v) { " +
          "return v === '#00000000' || s.apply(CSS, arguments); }; })()")
        again = browser.lines.size
        _ <- browser.navigate(url)
        _ <- heard("playerbar ready", again, 100)
        _ <- Task.sleep(500.millis)
        lg <- browser.value(layered)
        // the video's source let go, as a player closing lets it go: its boxes unmarked, and what follows them no layer
        _ <- browser.value("var v = document.querySelector('video'); window.__marked = " +
          "document.querySelectorAll('.everyui-video-path').length; v.removeAttribute('src'); v.load(); return 0")
        _ <- Task.sleep(800.millis)
        unmarked <- browser.value("return [window.__marked, document.querySelectorAll('.everyui-video-path').length]")
      } yield {
        check("playerbar: the video playing", since.exists(_.contains("playerbar ready")))
        drawn("bar", barApart)
        val (rule, boxes) = if (lg.isArr) (lg.asVector(0).asBoolean, lg.asVector(1).asInt) else (false, -1)
        check("playerbar: where the engine reads #rrggbbaa, the over-video rule " + (if (squashes) "on" else "off") +
          " in Chrome " + browser.major + " (the rule " + (if (rule) "found" else "not found") + ", " + boxes +
          " box(es) on the bar's way up given a transform)",
          lg.isArr && rule == squashes && (boxes > 0) == squashes)
        val (held, left) = if (unmarked.isArr) (unmarked.asVector(0).asInt, unmarked.asVector(1).asInt) else (-1, -1)
        check("playerbar: the boxes holding the video marked while it has a source and not after (" + held + " then " +
          left + ")", (held > 0) == squashes && left == 0)
        overs.foreach { case (name, mean) => drawn(name, mean) }
        val errors = since.filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR"))
        check("playerbar: no error left uncaught" + errors.headOption.map(": " + _).getOrElse(""), errors.isEmpty)
      }
    }
    DevTools.around(chrome) { browser =>
      println("[old chrome] Chrome " + browser.major + ": " + chrome)
      browser.onLine { line =>
        println(line)
        System.out.flush()
      }
      def key(code: Int, name: String): Task[Unit] = for {
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("rawKeyDown"), "windowsVirtualKeyCode" -> num(code),
          "key" -> str(name)))
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("keyUp"), "windowsVirtualKeyCode" -> num(code),
          "key" -> str(name)))
        _ <- Task.sleep(250.millis)
      } yield ()
      def mouse(kind: String, x: Double, y: Double): Task[Unit] = browser.send("Input.dispatchMouseEvent", obj(
        "type" -> str(kind), "x" -> num(x.round.toInt), "y" -> num(y.round.toInt), "button" -> str("left"), "clickCount" -> num(1))).unit
      def demo(name: String): Task[Unit] = {
        val before = browser.lines.size
        def errors: List[String] = browser.lines.toArray.drop(before).map(_.toString)
          .filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR")).toList
        for {
          _ <- browser.navigate(page + "?demo=" + name)
          _ <- Task.sleep(3.seconds)
          drawn <- browser.value("var n = 0; [].forEach.call(document.querySelectorAll('#app *'), function (e) { " +
            "var r = e.getBoundingClientRect(); if (r.width > 0 && r.height > 0) n++; }); return n")
          _ <- shots.map(d => browser.screenshot(d.resolve(name + ".png"))).getOrElse(Task.unit)
          _ = check(name + ": its screen drawn (" + drawn.asInt + " boxes shown)", drawn.asInt >= 5)
          _ <- key(40, "ArrowDown")
          _ <- key(39, "ArrowRight")
          _ <- key(38, "ArrowUp")
          _ <- mouse("mouseMoved", 960.0, 540.0)
          _ <- mouse("mouseMoved", 700.0, 300.0)
          _ <- mouse("mousePressed", 700.0, 300.0)
          _ <- mouse("mouseReleased", 700.0, 300.0)
          _ <- Task.sleep(1.second)
          _ <- shots.map(d => browser.screenshot(d.resolve(name + "-after.png"))).getOrElse(Task.unit)
          _ = check(name + ": no error left uncaught" + errors.headOption.map(": " + _).getOrElse(""), errors.isEmpty)
        } yield ()
      }
      // the net demo's request through Spice, to a server of our own, its answer said in the page's console
      def net(): Task[Unit] = Task.defer {
        val before = browser.lines.size
        def since: List[String] = browser.lines.toArray.drop(before).map(_.toString).toList
        val asked = new AtomicInteger(0)
        val answer = """{"a":1,"b":[true,null,"x"]}"""
        val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0)
        server.createContext("/", exchange => {
          asked.incrementAndGet()
          val body = answer.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Access-Control-Allow-Origin", "*")
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(200, body.length.toLong)
          exchange.getResponseBody.write(body)
          exchange.close()
        })
        server.start()
        val url = "http://127.0.0.1:" + server.getAddress.getPort + "/net"
        def done(tries: Int): Task[Unit] =
          if (tries == 0 || since.exists(_.contains("net done"))) Task.unit
          else Task.sleep(250.millis).flatMap(_ => done(tries - 1))
        (for {
          _ <- browser.navigate(page + "?demo=net&public=" + url)
          _ <- done(80)
          _ <- shots.map(d => browser.screenshot(d.resolve("net.png"))).getOrElse(Task.unit)
          said = since.find(_.contains("net https ")).getOrElse("nothing said")
          errors = since.filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR"))
          _ = check("net: a request through Spice answered (" + said.trim + ")", said.contains("net https ok"))
          _ = check("net: its JSON object read with fabric (" + said.trim + ")", said.contains("json " + answer))
          _ = check("net: the request heard by the server (" + asked.get + ")", asked.get > 0)
          _ = check("net: no error left uncaught" + errors.headOption.map(": " + _).getOrElse(""), errors.isEmpty)
        } yield ()).guarantee(Task(server.stop(0)))
      }
      // the motionstop demo's motions taken away mid-cycle: each node drawn as it has itself, read from the engine's
      // computed style (its transform's turn and scale, its translation, its opacity, and in an engine with them the
      // `rotate`, `scale` and `translate` an animation moves)
      def motionstop(): Task[Unit] = Task.defer {
        val before = browser.lines.size
        def since: List[String] = browser.lines.toArray.drop(before).map(_.toString).toList
        def done(tries: Int): Task[Unit] =
          if (tries == 0 || since.exists(_.contains("motionstop taken away"))) Task.unit
          else Task.sleep(100.millis).flatMap(_ => done(tries - 1))
        val look = "function look(name) { var e = document.querySelector('[aria-label=\"' + name + '\"]'); " +
          "if (!e) return name + ' missing'; var s = getComputedStyle(e); var m = s.transform || s.webkitTransform; " +
          "var a = 1, b = 0, x = 0, y = 0; if (m && m != 'none') { var v = m.slice(m.indexOf('(') + 1, -1).split(','); " +
          "a = +v[0]; b = +v[1]; x = +v[4]; y = +v[5]; } " +
          "return name + ' turn=' + Math.round(Math.atan2(b, a) * 1800 / Math.PI) / 10 + ' scale=' + " +
          "Math.round(Math.sqrt(a * a + b * b) * 1000) / 1000 + ' x=' + x + ' y=' + y + ' opacity=' + " +
          "Math.round(+s.opacity * 1000) / 1000 + ' rotate=' + (s.rotate || 'none') + ' scaled=' + (s.scale || 'none') + " +
          "' translate=' + (s.translate || 'none'); } " +
          "return ['motion-tick', 'motion-turn', 'motion-scale', 'motion-fade', 'motion-slide', 'motion-swap'].map(look).join('\\n')"
        for {
          _ <- browser.navigate(page + "?demo=motionstop")
          _ <- done(100)
          _ <- Task.sleep(400.millis)
          read <- browser.value(look)
          _ <- shots.map(d => browser.screenshot(d.resolve("motionstop.png"))).getOrElse(Task.unit)
        } yield {
          val drawn = read.asString.split("\n").toList.map { line =>
            val words = line.split(" ").toList
            words.head -> words.tail.flatMap(w => w.split("=", 2).toList match {
              case k :: v :: Nil => Some(k -> v)
              case _ => None
            }).toMap
          }.toMap
          println("[old chrome] motionstop: " + read.asString.replace("\n", "; "))
          def of(name: String, key: String): String = drawn.get(name).flatMap(_.get(key)).getOrElse("?")
          def near(name: String, key: String, want: Double): Boolean =
            of(name, key).toDoubleOption.exists(v => math.abs(v - want) < 0.01)
          // nothing an animation moves left on the node itself
          def unmoved(name: String): Boolean = List("rotate", "scaled", "translate").forall(k => of(name, k) == "none")
          check("motionstop: taken away (" + since.exists(_.contains("motionstop taken away")) + ")",
            since.exists(_.contains("motionstop taken away")))
          check("motionstop: the done tick upright and whole", near("motion-tick", "turn", 0.0) &&
            near("motion-tick", "opacity", 1.0) && unmoved("motion-tick"))
          check("motionstop: the turn back to its own 18 degrees", near("motion-turn", "turn", 18.0) &&
            near("motion-turn", "scale", 1.0) && unmoved("motion-turn"))
          check("motionstop: the scale back to its own half", near("motion-scale", "scale", 0.5) &&
            unmoved("motion-scale"))
          check("motionstop: the fade back to its own 0.8", near("motion-fade", "opacity", 0.8))
          check("motionstop: the slide back to where it is placed", near("motion-slide", "x", 0.0) &&
            near("motion-slide", "y", 0.0) && unmoved("motion-slide"))
          check("motionstop: the turn replaced by a fade at no tilt, fading", near("motion-swap", "turn", 0.0) &&
            unmoved("motion-swap") && of("motion-swap", "opacity").toDoubleOption.exists(_ < 0.99))
          val errors = since.filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR"))
          check("motionstop: no error left uncaught" + errors.headOption.map(": " + _).getOrElse(""), errors.isEmpty)
        }
      }
      for {
        // (an error's whole stack, for the console)
        _ <- browser.addScript("Error.stackTraceLimit = 50")
        _ <- browser.size(1920, 1080)
        _ <- demos.filterNot(d => d == "corners" || d == "playerbar")
          .map(d => if (d == "net") net() else if (d == "motionstop") motionstop() else demo(d))
          .foldLeft(Task.unit)((a, b) => a.flatMap(_ => b))
      } yield ()
    }.flatMap(_ => if (demos.contains("corners")) DevTools.around(chrome, gpu = true)(corners) else Task.unit)
    .flatMap(_ => if (demos.contains("playerbar")) DevTools.around(chrome, playerAgent, gpu = true)(playerbar) else Task.unit)
    .map { _ =>
      println(if (failed.isEmpty) "[old chrome] all passed" else "[old chrome] failed: " + failed.mkString("; "))
      if (failed.nonEmpty) sys.exit(1)
    }
  }
}
