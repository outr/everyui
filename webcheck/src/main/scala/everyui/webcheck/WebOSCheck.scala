package everyui.webcheck

import fabric.*
import rapid.*

import java.nio.file.Paths
import scala.concurrent.duration.*

/** The example app (its web build: `demo=tv`, TvDemo) as an LG TV runs it, in a headless Chrome made to look like one:
  * webOS 24's user agent, and `webOSSystem` stubbed before the page's scripts, with launch parameters, the TV's
  * deviceInfo and a `platformBack` that records being called, and the Luna bus as `PalmServiceBridge` (and later
  * webOSTV.js's `webOS.service`), which records each call and answers it as the Application Manager would. Driven by
  * the events the TV sends, it checks that:
  *
  *  - the app is told it is on webOS 24, and starts with the launch parameters (`demo=tv`, `open`);
  *  - OK opens the details, and the remote's Back (key code 461) comes back from them, leaving nothing;
  *  - the page's faces load from its own files (file://);
  *  - Back at Home leaves the app by the runtime's `platformBack()`, once;
  *  - a `webOSRelaunch` reaches the app as a launch, with its parameters;
  *  - another app is asked for (`getAppLoadStatus`) and launched (`launch`) through the runtime's PalmServiceBridge,
  *    and through webOSTV.js's `webOS.service.request` where the app has it, one not installed read so;
  *  - the Magic Remote's pointer focuses what it comes onto while it shows (`cursorStateChange`), and not while hidden;
  *  - the page hidden and shown again is away and resumed;
  *  - the screen is 960 by 540 units at 1080p, a box 100 units across 200 CSS pixels, at a scale of 2 (2.33 where the
  *    page's pixel ratio is the Simulator's 1.1667);
  *  - Home, which scrolls, shows no scrollbar, and the page itself runs past the window on neither side;
  *  - on a sign-in form, the arrows pass over its fields without the TV's keyboard; OK puts the field's input in the
  *    page's focus, which brings the keyboard up, and submits nothing; what is typed arrives, Left moving the caret;
  *    the remote's Back puts the keyboard away and goes nowhere, and once it is away OK starts the typing again (and no
  *    submit), an arrow or Up leaves the field and Back goes back; a Back that reaches the page while a field is typed
  *    in, with no word from the keyboard, lets the field go and goes nowhere, and Down then moves on; the keyboard's
  *    Enter ends the typing and submits, the focus kept.
  *
  * The TV's keyboard is stubbed as LG's webOS TV Simulator has it: it comes up as a field takes the page's focus and
  * goes down as it is let go, saying so with `keyboardStateChange`; while it is up, a key that reaches the page is kept
  * from it but Enter, Left and Right (stopped at the window, as the Simulator stops it), and the remote's Back, or Up or
  * Down off its edge, put it away, the field still focused (`everyuiRemote`).
  *
  * Prints the page's console and each check; exits 1 where one fails.
  *
  *   WebOSCheck <page dir> [--palm | --simulator] [--chrome <binary>]
  *
  * The page dir is the app as `webosPackage` stages it (target/webos/app), loaded from its files as the TV loads it.
  * `--palm` stubs `PalmSystem` alone, as webOS before 5 has it, in place of `webOSSystem`. `--simulator` is the page as
  * LG's webOS TV 26 Simulator gives it: `webOSSystem` with `PalmSystem` the same object, no `webOS`, a pixel ratio of
  * 1.1667, and a window of no size that is given its 1920 by 1080 after the page starts.
  * `--chrome` names the browser (else the Chrome on the path, headless). One older than 68 (an old TV's engine: webOS 4
  * has Chromium 53, Tizen 4 has 56) is driven as webOS 4, its user agent's and its version "4", in a window of its own
  * (it has no headless mode: run it under `xvfb-run`); the check is the same.
  */
object WebOSCheck extends RapidApp {
  // the user agent of a webOS TV whose engine is `chrome`
  private def agent(chrome: String): String =
    "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome +
      " Safari/537.36 WebAppManager"

  // the runtime's object (webOSSystem; PalmSystem alone; or both, one object, as the Simulator has them) and the TV's
  // keyboard on the screen
  private def stub(palm: Boolean, simulator: Boolean): String = {
    val system = if (palm) "PalmSystem" else "webOSSystem"
    s"""window.everyuiExits = [];
      |window.$system = {
      |  launchParams: JSON.stringify({ demo: "tv", open: "/title/12" }),
      |  deviceInfo: JSON.stringify({ modelName: "OLED55C4", platformVersionMajor: "9" }),
      |  platformBack: function () { window.everyuiExits.push("$system"); }
      |};
      |${if (simulator) "Object.defineProperty(window, \"PalmSystem\", { value: window.webOSSystem });" else ""}
      |window.everyuiKeyboard = false;
      |window.everyuiKeyboards = 0;
      |const everyuiShow = v => {
      |  if (window.everyuiKeyboard == v) return;
      |  window.everyuiKeyboard = v;
      |  if (v) window.everyuiKeyboards++;
      |  document.dispatchEvent(new CustomEvent("keyboardStateChange", { detail: { visibility: v } }));
      |};
      |const everyuiTyped = e => e instanceof HTMLInputElement &&
      |  ["text", "email", "password", "search", "url", "tel", "number"].includes(e.type);
      |window.addEventListener("focus", e => { if (everyuiTyped(e.target)) everyuiShow(true); }, true);
      |window.addEventListener("blur", e => { if (everyuiTyped(e.target)) everyuiShow(false); }, true);
      |for (const t of ["keydown", "keyup", "keypress"]) window.addEventListener(t, e => {
      |  if (window.everyuiKeyboard && !["Enter", "ArrowLeft", "ArrowRight", "Unidentified"].includes(e.key))
      |    e.stopPropagation();
      |}, true);
      |window.everyuiRemote = name => {
      |  if (window.everyuiKeyboard) { everyuiShow(false); return; }
      |  const code = { Back: 461, Up: 38, Down: 40 }[name];
      |  const key = { Back: "GoBack", Up: "ArrowUp", Down: "ArrowDown" }[name];
      |  for (const t of ["keydown", "keyup"]) {
      |    const e = new KeyboardEvent(t, { key, keyCode: code, which: code, bubbles: true, cancelable: true });
      |    if (e.keyCode != code) for (const k of ["keyCode", "which"]) Object.defineProperty(e, k, { get: () => code });
      |    document.activeElement.dispatchEvent(e);
      |  }
      |};
      |window.everyuiLuna = [];
      |const everyuiAnswer = (uri, params) => {
      |  window.everyuiLuna.push(uri + " " + params);
      |  const there = JSON.parse(params).id != "missing" && JSON.parse(params).appId != "missing";
      |  return uri.endsWith("/getAppLoadStatus") ? { returnValue: true, exist: there }
      |    : there ? { returnValue: true } : { returnValue: false, errorCode: -101, errorText: "no such app" };
      |};
      |window.PalmServiceBridge = function () {
      |  const bridge = this;
      |  bridge.call = (uri, params) => setTimeout(() =>
      |    bridge.onservicecallback(JSON.stringify(everyuiAnswer(uri, params))), 10);
      |};
      |window.everyuiService = () => { window.webOS = { service: { request: (uri, o) => setTimeout(() => {
      |  const r = everyuiAnswer(uri + "/" + o.method, JSON.stringify(o.parameters));
      |  (r.returnValue ? o.onSuccess : o.onFailure)(r);
      |}, 10) } }; };
      |window.everyuiVisible = "visible";
      |Object.defineProperty(document, "visibilityState", { get: () => window.everyuiVisible, configurable: true });
      |Object.defineProperty(document, "hidden", { get: () => window.everyuiVisible == "hidden", configurable: true });
      |""".stripMargin
  }

  override def run(args: List[String]): Task[Unit] = {
    val palm = args.contains("--palm")
    val simulator = args.contains("--simulator")
    val system = if (palm) "PalmSystem" else "webOSSystem"
    val chrome = args.dropWhile(_ != "--chrome").drop(1).headOption.getOrElse(DevTools.defaultChrome)
    // webOS 4 on an engine older than webOS 5's, else webOS 24
    val (version, engine) = if (DevTools.majorOf(chrome) < 68) ("4", "53.0.2785.34") else ("24", "108.0.5359.211")
    val ratio = if (simulator) 1.1667 else 1.0
    val scale = if (simulator) "2.33" else "2"
    val usage = "usage: WebOSCheck <page dir> [--palm | --simulator] [--chrome <binary>]"
    val page = Paths.get(args.headOption.getOrElse(sys.error(usage))).toAbsolutePath
      .resolve("index.html").toUri.toString
    val lines = new java.util.concurrent.CopyOnWriteArrayList[String]()
    var failed = List.empty[String]
    def check(what: String, ok: Boolean): Unit = {
      println((if (ok) "[webos] ok " else "[webos] FAILED ") + what)
      System.out.flush()
      if (!ok) failed :+= what
    }
    DevTools.around(chrome, Some(agent(engine))) { browser =>
      browser.onLine { line =>
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
      // (its key code set on the event where the engine takes none from its init, as before Chromium 63)
      def key(code: Int, name: String): Task[Unit] = browser.eval(
        s"""for (const t of ["keydown", "keyup"]) {
           |  const e = new KeyboardEvent(t, { key: "$name", keyCode: $code, which: $code, bubbles: true });
           |  if (e.keyCode != $code)
           |    for (const k of ["keyCode", "which"]) Object.defineProperty(e, k, { get: () => $code });
           |  document.dispatchEvent(e);
           |}
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
        _ <- browser.send("Input.dispatchMouseEvent", obj("type" -> str("mouseMoved"), "x" -> num(5), "y" -> num(1000)))
        _ <- browser.send("Input.dispatchMouseEvent", obj("type" -> str("mouseMoved"), "x" -> num(xy(0).round.toInt),
          "y" -> num(xy(1).round.toInt)))
        _ <- Task.sleep(300.millis)
      } yield ()
      // a key as it reaches the page, to what has the page's focus (a field, when one has it)
      def press(code: Int, name: String): Task[Unit] = for {
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("rawKeyDown"), "windowsVirtualKeyCode" -> num(code),
          "key" -> str(name)))
        _ <- if (code == 13) browser.send("Input.dispatchKeyEvent", obj("type" -> str("char"),
          "windowsVirtualKeyCode" -> num(13), "key" -> str("Enter"), "text" -> str("\r"))) else Task.unit
        _ <- browser.send("Input.dispatchKeyEvent", obj("type" -> str("keyUp"), "windowsVirtualKeyCode" -> num(code),
          "key" -> str(name)))
        _ <- Task.sleep(300.millis)
      } yield ()
      // a key of the remote's, which the TV's keyboard answers itself while it is up ("Back", "Up", "Down")
      def remote(name: String): Task[Unit] =
        browser.eval(s"""everyuiRemote("$name"); return 0""").flatMap(_ => Task.sleep(300.millis))
      def value(js: String): Task[Json] = browser.value(js)
      // what has the page's focus: "field" for an input, else its tag
      def active: Task[String] =
        value("const a = document.activeElement; return a.tagName == 'INPUT' ? 'field' : a.tagName")
        .map(_.asString)
      def keyboard: Task[(Boolean, Int)] = value("return [window.everyuiKeyboard, window.everyuiKeyboards]")
        .map(v => (v.asVector(0).asBoolean, v.asVector(1).asInt))
      def typed: Task[String] =
        value("return document.querySelector('#app input[type=email], #app input[type=text]').value")
        .map(_.asString)
      def exits: Task[Int] = browser.eval("return window.everyuiExits.length").map(_("result")("value").asInt)
      def visible(state: String): Task[Unit] = browser.eval(
        s"""window.everyuiVisible = "$state"; document.dispatchEvent(new Event("visibilitychange")); return 0""").unit
      // the page `width` by `height`, at the pixel ratio of the run
      def window(width: Int, height: Int): Task[Unit] = browser.send("Emulation.setDeviceMetricsOverride",
        obj("width" -> num(width), "height" -> num(height), "deviceScaleFactor" -> num(ratio), "mobile" -> bool(false),
          "fitWindow" -> bool(false))).unit
      for {
        _ <- browser.addScript(stub(palm, simulator))
        // the page 1920 by 1080, as a TV's is at 1080p; in the Simulator, a window of no size until the page has started
        _ <- if (simulator) window(1, 1) else window(1920, 1080)
        _ <- browser.navigate(page)
        home <- heard(s"tv: home shown (kind WebOS, version $version, open=/title/12)")
        _ = check(s"starts on webOS $version with its launch parameters (" + system + ")", home)
        _ <- if (simulator) Task.sleep(500.millis).flatMap(_ => window(1920, 1080)) else Task.unit
        screen <- heard("tv: screen 960x540 at " + scale)
        ruler <- value("const r = document.querySelector('[aria-label=ruler]').getBoundingClientRect(); return r.width")
        _ = check("the screen is 960 by 540 units at a scale of " + scale + ", 100 units 200 CSS pixels",
          screen && math.abs(ruler.asDouble - 200.0) < 0.5)
        bars <- value(
          """const all = [...document.querySelectorAll("#app *")];
            |const d = document.documentElement;
            |return [all.some(e => e.scrollHeight > e.clientHeight + 1 && getComputedStyle(e).overflowY == "auto"),
            |  all.filter(e => e.offsetWidth - e.clientWidth - e.clientLeft * 2 > 0).length,
            |  d.scrollWidth <= innerWidth && d.scrollHeight <= innerHeight &&
            |    document.body.scrollWidth <= innerWidth && document.body.scrollHeight <= innerHeight]""".stripMargin)
        _ = check("Home scrolls with no scrollbar, and the page runs past the window on neither side",
          bars.asVector(0).asBoolean && bars.asVector(1).asInt == 0 && bars.asVector(2).asBoolean)
        // words drawn in Open Sans measure apart from the fallback's (the set's word is not to be trusted: Chromium
        // 53's can call a face unloaded that the words are drawn in)
        faces <- value("const w = f => { const s = document.createElement('span'); s.style.font = '24px ' + f; " +
          "s.textContent = 'Hamburgefonstiv 0123'; document.body.appendChild(s); const x = s.offsetWidth; s.remove(); " +
          "return x; }; return w(\"'Open Sans', monospace\") != w('monospace')")
        _ = check("the page's faces load from its own files", faces.asBoolean)
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
        // another app opened, through the runtime's PalmServiceBridge, then through webOSTV.js's webOS.service
        _ <- event("webOSRelaunch", """{ other: "netflix", target: "https://www.netflix.com/watch/80100172" }""")
        bridged <- heard("tv: launched netflix true")
        bridgeCalls <- value("return window.everyuiLuna")
        _ = check("another app is asked for and launched through PalmServiceBridge",
          bridged && count("tv: installed netflix true") == 1 && bridgeCalls.asVector.map(_.asString) == Vector(
            "luna://com.webos.applicationManager/getAppLoadStatus {\"appId\":\"netflix\"}",
            "luna://com.webos.applicationManager/launch {\"id\":\"netflix\",\"params\":" +
              "{\"contentTarget\":\"https://www.netflix.com/watch/80100172\"}}"))
        _ <- browser.eval("everyuiService(); window.everyuiLuna = []; return 0")
        _ <- event("webOSRelaunch", """{ other: "missing" }""")
        refused <- heard("tv: launched missing false")
        serviceCalls <- value("return window.everyuiLuna")
        _ = check("one not installed reads so, and is not launched, through webOS.service",
          refused && count("tv: installed missing false") == 1 && serviceCalls.asVector.map(_.asString) == Vector(
            "luna://com.webos.applicationManager/getAppLoadStatus {\"appId\":\"missing\"}",
            "luna://com.webos.applicationManager/launch {\"id\":\"missing\",\"params\":{}}"))
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
        // the sign-in form: Home's third button, from the focus left on Settings
        _ <- press(40, "ArrowDown")
        _ <- press(13, "Enter")
        form <- heard("tv: focus Container \"Email\"")
        _ <- press(40, "ArrowDown")
        down <- heard("tv: focus Container \"Password\"")
        _ <- press(38, "ArrowUp")
        up <- heard("tv: focus Container \"Email\"", 2)
        passed <- active
        before <- keyboard
        _ = check("the arrows pass over a field, its input unfocused and no keyboard up",
          form && down && up && passed == "BODY" && before == ((false, 0)))
        _ <- press(13, "Enter")
        editing <- active
        raised <- keyboard
        _ = check("OK on a field puts its input in the page's focus, the TV's keyboard up, and submits nothing",
          editing == "field" && raised == ((true, 1)) && count("tv: signed in") == 0 &&
            count("tv: focus Container \"Password\"") == 1)
        _ <- browser.insertText("ada@example.com")
        _ <- press(37, "ArrowLeft")
        words <- typed
        caret <- value("return document.activeElement.selectionStart").map(_.asInt)
        _ = check("what is typed arrives, and Left moves the caret in it", words == "ada@example.com" && caret == 14)
        homes = count("tv: home shown")
        _ <- remote("Back")
        put <- keyboard
        still <- active
        _ = check("the remote's Back while typing puts the keyboard away and goes nowhere",
          !put._1 && count("tv: home shown") == homes && still == "field")
        _ <- press(13, "Enter")
        again <- keyboard
        _ = check("OK once the keyboard is away starts the typing again, and submits nothing",
          again == ((true, 2)) && count("tv: signed in") == 0 && count("tv: focus Container \"Password\"") == 1)
        _ <- remote("Back")
        _ <- press(40, "ArrowDown")
        leaves <- heard("tv: focus Container \"Password\"", 2)
        gone <- active
        _ = check("an arrow once the keyboard is away leaves the field", leaves && gone == "BODY")
        _ <- press(13, "Enter")
        _ <- browser.insertText("secret")
        _ <- remote("Up")
        _ <- press(38, "ArrowUp")
        upward <- heard("tv: focus Container \"Email\"", 3)
        _ = check("Up once the keyboard is away leaves the field", upward)
        // a Back that reaches the page while a field is typed in, the keyboard saying nothing
        _ <- press(13, "Enter")
        _ <- press(461, "GoBack")
        let <- active
        quiet <- keyboard
        _ = check("a Back that reaches the page while typing lets the field go and goes nowhere",
          let == "BODY" && !quiet._1 && count("tv: home shown") == homes && count("tv: focus Container \"Email\"") == 3)
        _ <- press(40, "ArrowDown")
        on <- heard("tv: focus Container \"Password\"", 3)
        _ = check("and Down then moves on", on)
        _ <- press(13, "Enter")
        moves = count("tv: focus")
        _ <- press(13, "Enter")
        signed <- heard("tv: signed in as ada@example.com (password 6 characters)")
        done <- keyboard
        ended <- active
        _ = check("the keyboard's Enter ends the typing and submits, the focus kept",
          signed && !done._1 && ended == "BODY" && count("tv: focus") == moves)
        _ <- press(13, "Enter")
        _ <- remote("Back")
        _ <- press(461, "GoBack")
        back <- heard("tv: home shown", homes + 1)
        _ = check("Back once the keyboard is away goes back", back)
        errors = lines.toArray.map(_.toString).filter(l => l.toLowerCase.contains("uncaught") || l.contains("ERROR"))
        _ = check("no errors on the console", errors.isEmpty)
      } yield ()
    }.map { _ =>
      println(if (failed.isEmpty) "[webos] all passed" else "[webos] failed: " + failed.mkString("; "))
      if (failed.nonEmpty) sys.exit(1)
    }
  }
}
