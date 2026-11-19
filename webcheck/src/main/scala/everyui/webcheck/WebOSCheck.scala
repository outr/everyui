package everyui.webcheck

import fabric.*
import rapid.*
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.nio.file.Paths
import scala.concurrent.duration.*

/** The example app (its web build: `demo=tv`, TvDemo) as an LG TV runs it, in a headless Chrome made to look like one:
  * webOS 24's user agent, and `webOSSystem` stubbed before the page's scripts, with launch parameters, the TV's
  * deviceInfo and a `platformBack` that records being called. Driven by the events the TV sends, it checks that:
  *
  *  - the app is told it is on webOS 24, and starts with the launch parameters (`demo=tv`, `open`);
  *  - OK opens the details, and the remote's Back (key code 461) comes back from them, leaving nothing;
  *  - the page's faces load from its own files (file://);
  *  - Back at Home leaves the app by the runtime's `platformBack()`, once;
  *  - a `webOSRelaunch` reaches the app as a launch, with its parameters;
  *  - the Magic Remote's pointer focuses what it comes onto while it shows (`cursorStateChange`), and not while hidden;
  *  - the page hidden and shown again is away and resumed.
  *
  * Prints the page's console and each check; exits 1 where one fails.
  *
  *   WebOSCheck <page dir> [--palm]
  *
  * The page dir is the app as `webosPackage` stages it (target/webos/app), loaded from its files as the TV loads it.
  * `--palm` stubs `PalmSystem` alone, as LG's webOS TV Simulator has it, in place of `webOSSystem`.
  */
object WebOSCheck extends RapidApp {
  private val agent = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/108.0.5359.211 Safari/537.36 WebAppManager"

  // the runtime's object: webOSSystem, or PalmSystem alone, as LG's webOS TV Simulator has it
  private def stub(palm: Boolean): String = {
    val system = if (palm) "PalmSystem" else "webOSSystem"
    s"""window.everyuiExits = [];
      |window.$system = {
      |  launchParams: JSON.stringify({ demo: "tv", open: "/title/12" }),
      |  deviceInfo: JSON.stringify({ modelName: "OLED55C4", platformVersionMajor: "9" }),
      |  platformBack: function () { window.everyuiExits.push("$system"); }
      |};
      |window.everyuiVisible = "visible";
      |Object.defineProperty(document, "visibilityState", { get: () => window.everyuiVisible, configurable: true });
      |Object.defineProperty(document, "hidden", { get: () => window.everyuiVisible == "hidden", configurable: true });
      |""".stripMargin
  }

  override def run(args: List[String]): Task[Unit] = {
    val palm = args.contains("--palm")
    val system = if (palm) "PalmSystem" else "webOSSystem"
    val page = Paths.get(args.headOption.getOrElse(sys.error("usage: WebOSCheck <page dir> [--palm]"))).toAbsolutePath
      .resolve("index.html").toUri.toString
    val lines = new java.util.concurrent.CopyOnWriteArrayList[String]()
    var failed = List.empty[String]
    def check(what: String, ok: Boolean): Unit = {
      println((if (ok) "[webos] ok " else "[webos] FAILED ") + what)
      System.out.flush()
      if (!ok) failed :+= what
    }
    RoboBrowser.withBrowser(RoboBrowserConfig(
      browserConfig = BrowserConfig(noSandbox = true, extraArgs = List("--mute-audio", "--window-size=1920,1080",
        "--user-agent=" + agent)),
      tabSelector = TabSelector.FirstPage
    )) { browser =>
      browser.event.runtime.consoleAPICalled.attach { event =>
        val line = event.args.map(_.value).mkString(" ")
        lines.add(line)
        println(line)
        System.out.flush()
      }
      def count(part: String): Int = lines.toArray.count(_.toString.contains(part))
      // `times` lines containing `part`, within ten seconds
      def heard(part: String, times: Int = 1): Task[Boolean] = {
        def look(left: Int): Task[Boolean] =
          if (count(part) >= times) Task.pure(true)
          else if (left == 0) Task.pure(false)
          else Task.sleep(100.millis).flatMap(_ => look(left - 1))
        look(100)
      }
      def key(code: Int, name: String): Task[Unit] = browser.eval(
        s"""for (const t of ["keydown", "keyup"])
           |  document.dispatchEvent(new KeyboardEvent(t, { key: "$name", keyCode: $code, which: $code, bubbles: true }));
           |return 0""".stripMargin).unit
      def event(name: String, detail: String): Task[Unit] =
        browser.eval(s"""document.dispatchEvent(new CustomEvent("$name", { detail: $detail })); return 0""").unit
      // the pointer moved onto the words `label`, as the Magic Remote moves it
      def pointTo(label: String): Task[Unit] = for {
        at <- browser.eval(
          s"""const all = [...document.querySelectorAll("#app *")].filter(e => e.textContent.trim() == "$label" &&
             |  e.getBoundingClientRect().width > 0);
             |const r = all[all.length - 1].getBoundingClientRect();
             |return [r.left + r.width / 2, r.top + r.height / 2]""".stripMargin)
        xy = at("result")("value").asVector.map(_.asDouble)
        _ <- browser.send("Input.dispatchMouseEvent", obj("type" -> str("mouseMoved"), "x" -> num(5.0), "y" -> num(1000.0)))
        _ <- browser.send("Input.dispatchMouseEvent", obj("type" -> str("mouseMoved"), "x" -> num(xy(0)), "y" -> num(xy(1))))
        _ <- Task.sleep(300.millis)
      } yield ()
      def exits: Task[Int] = browser.eval("return window.everyuiExits.length").map(_("result")("value").asInt)
      def visible(state: String): Task[Unit] = browser.eval(
        s"""window.everyuiVisible = "$state"; document.dispatchEvent(new Event("visibilitychange")); return 0""").unit
      for {
        _ <- browser.enableRuntime
        _ <- browser.send("Page.enable")
        _ <- browser.send("Page.addScriptToEvaluateOnNewDocument", obj("source" -> str(stub(palm))))
        _ <- browser.navigate(page)
        _ <- browser.waitForLoaded()
        home <- heard("tv: home shown (kind WebOS, version 24, open=/title/12)")
        _ = check("starts on webOS 24 with its launch parameters (" + system + ")", home)
        faces <- browser.eval("return [...document.fonts].some(f => f.family.replace(/[\"']/g, '') == 'Open Sans' && f.status == 'loaded')")
        _ = check("the page's faces load from its own files", faces("result")("value").asBoolean)
        _ <- heard("tv: focus Container \"Open details\"")
        _ <- key(13, "Enter")
        details <- heard("tv: details shown")
        _ = check("OK opens the details", details)
        _ <- Task.sleep(300.millis)
        _ <- key(461, "GoBack")
        back <- heard("tv: focus Container \"Open details\"", 2).map(_ && count("tv: home shown") == 2)
        left <- exits
        _ = check("Back from the details goes Home, and does not leave", back && left == 0)
        _ <- Task.sleep(300.millis)
        _ <- key(461, "Unidentified")
        _ <- heard("webOS: leaving by " + system)
        once <- exits
        _ = check("Back at Home leaves by " + system + ".platformBack(), once", once == 1)
        _ <- event("webOSRelaunch", """{ open: "/title/7", contentId: "7" }""")
        relaunch <- heard("tv: launch contentId=7 open=/title/7")
        _ = check("a relaunch reaches the app with its parameters", relaunch)
        _ <- pointTo("Settings")
        hidden = count("tv: focus Container \"Settings\"") == 0
        _ <- event("cursorStateChange", "{ visibility: true }")
        _ <- pointTo("Settings")
        pointed <- heard("tv: focus Container \"Settings\"")
        _ = check("the pointer focuses what it comes onto while it shows, not while it is hidden", hidden && pointed)
        _ <- event("cursorStateChange", "{ visibility: false }")
        _ <- pointTo("Open details")
        stays = count("tv: focus Container \"Open details\"") == 2
        _ = check("hidden, the pointer leaves the focus where it is", stays)
        _ <- visible("hidden")
        away <- heard("tv: away")
        _ <- visible("visible")
        resumed <- heard("tv: resumed")
        _ = check("hidden and shown is away and resumed", away && resumed)
        errors = lines.toArray.map(_.toString).filter(l => l.contains("uncaught") || l.contains("ERROR"))
        _ = check("no errors on the console", errors.isEmpty)
      } yield ()
    }.map { _ =>
      println(if (failed.isEmpty) "[webos] all passed" else "[webos] failed: " + failed.mkString("; "))
      if (failed.nonEmpty) sys.exit(1)
    }
  }
}
