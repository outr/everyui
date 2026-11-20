package everyui.webcheck

import fabric.*
import fabric.io.{JsonFormatter, JsonParser}
import rapid.*

import java.net.http.{HttpClient, HttpRequest, HttpResponse, WebSocket}
import java.net.{ServerSocket, URI}
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, CompletionStage, ConcurrentHashMap, CopyOnWriteArrayList}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

/** A Chrome of any age driven over its DevTools protocol at the page's own endpoint, with nothing between: what a 2016
  * TV's Chromium 38 speaks as well as today's. A Chrome from 59 on runs headless; an older one has no headless mode and
  * opens a window on `$DISPLAY` (run it under `xvfb-run`). Where the protocol changed, the older way is taken where the
  * newer one is refused (`addScript`, `insertText`, `size`: the Page domain's metrics before Chromium 39's Emulation),
  * and the page's console is read from either domain.
  *
  * Its profile is a new directory under `java.io.tmpdir`, removed with it. Or one already running, a TV's among them, is
  * driven where it is (`attach`): webOS 3 answers at `http://<tv>:9998` once Developer Mode is on, and only the socket
  * is closed after. */
final class DevTools private (process: Option[Process], profile: Option[Path], val major: Int, socket: WebSocket) {
  private val ids = new AtomicInteger(0)
  private val waiting = new ConcurrentHashMap[Int, CompletableFuture[Json]]()
  private val listeners = new CopyOnWriteArrayList[(String, Json) => Unit]()
  private val readers = new CopyOnWriteArrayList[String => Unit]()

  /** Every line the page writes to its console, and every error it leaves uncaught ("[uncaught] ..."), so far. */
  val lines: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()

  /** Hear each line of `lines` as it comes. */
  def onLine(f: String => Unit): Unit = readers.add(f)

  private def said(line: String): Unit = {
    lines.add(line)
    readers.forEach(_(line))
  }

  private[webcheck] def heard(message: Json): Unit = message.get("id") match {
    case Some(id) => Option(waiting.remove(id.asInt)).foreach(_.complete(message))
    case None =>
      val method = message("method").asString
      val params = message.get("params").getOrElse(obj())
      method match {
        case "Runtime.consoleAPICalled" =>
          said(params("args").asVector.map(a => a.get("value").map(text).getOrElse(
            a.get("description").map(_.asString).getOrElse(""))).mkString(" "))
        case "Console.messageAdded" if major < 54 =>
          // its words, and where it is an error, where it was thrown
          val m = params("message")
          val where = m.get("stack").orElse(m.get("stackTrace")).flatMap(s => s.get("callFrames").orElse(Some(s)))
            .filter(_.isArr).map(_.asVector.take(16).map(f => "\n    at " + f("functionName").asString + " (" +
              f("url").asString.split('/').last + ":" + f("lineNumber").asInt + ")").mkString).getOrElse("")
          said(m("text").asString + (if (m("level").asString == "error") where else ""))
        case "Runtime.exceptionThrown" =>
          val d = params("exceptionDetails")
          said("[uncaught] " + d.get("exception").flatMap(_.get("description")).map(_.asString)
            .getOrElse(d.get("text").map(_.asString).getOrElse("")))
        case _ => ()
      }
      listeners.forEach(_(method, params))
  }

  private def text(j: Json): String = j match {
    case Str(s, _) => s
    case other => JsonFormatter.Compact(other)
  }

  /** Hear each event the page sends: its method and params. */
  def listen(f: (String, Json) => Unit): Unit = listeners.add(f)

  /** A command and its whole answer: `result`, or `error` where the browser refused it. */
  def call(method: String, params: Json = obj()): Task[Json] = Task.defer {
    val id = ids.incrementAndGet()
    val answer = new CompletableFuture[Json]()
    waiting.put(id, answer)
    socket.sendText(JsonFormatter.Compact(obj("id" -> num(id), "method" -> str(method), "params" -> params)), true)
    Task(answer.get(60, java.util.concurrent.TimeUnit.SECONDS))
  }

  /** A command's result; a refusal fails. */
  def send(method: String, params: Json = obj()): Task[Json] = call(method, params).map { answer =>
    answer.get("error").foreach(e => throw new RuntimeException(method + " refused: " + JsonFormatter.Compact(e)))
    answer("result")
  }

  /** `body` run in the page as a function's: `{ result: { type, value } }`, as RoboBrowser's `eval` answers. */
  def eval(body: String): Task[Json] = send("Runtime.evaluate", obj(
    "expression" -> str("(function() { " + body + " })()"), "returnByValue" -> bool(true)))

  /** `body`'s value. */
  def value(body: String): Task[Json] = eval(body).map(_("result").get("value").getOrElse(Null))

  /** `source` run in every page loaded from now on, before its own scripts. */
  def addScript(source: String): Task[Unit] =
    call("Page.addScriptToEvaluateOnNewDocument", obj("source" -> str(source))).flatMap { answer =>
      if (answer.get("error").isEmpty) Task.unit
      else send("Page.addScriptToEvaluateOnLoad", obj("scriptSource" -> str(source))).unit
    }

  /** `text` typed into what has the page's focus, as an input method commits it; one character at a time where the
    * browser has no `Input.insertText` (Chromium 63). */
  def insertText(text: String): Task[Unit] = call("Input.insertText", obj("text" -> str(text))).flatMap { answer =>
    if (answer.get("error").isEmpty) Task.unit
    else text.map(c => send("Input.dispatchKeyEvent", obj("type" -> str("char"), "text" -> str(c.toString),
      "unmodifiedText" -> str(c.toString))).unit).foldLeft(Task.unit)((a, b) => a.flatMap(_ => b))
  }

  /** The page shown `width` by `height` CSS pixels, at `ratio` device pixels to each: the Emulation domain's, or
    * before it (Chromium 39) the Page domain's. */
  def size(width: Int, height: Int, ratio: Double = 1.0): Task[Unit] = call("Emulation.setDeviceMetricsOverride", obj(
    "width" -> num(width), "height" -> num(height), "deviceScaleFactor" -> num(ratio), "mobile" -> bool(false),
    "fitWindow" -> bool(false))).flatMap { answer =>
    if (answer.get("error").isEmpty) Task.unit
    else send("Page.setDeviceMetricsOverride", obj("width" -> num(width), "height" -> num(height),
      "deviceScaleFactor" -> num(ratio), "mobile" -> bool(false), "fitWindow" -> bool(false))).unit
  }

  /** `url` loaded, once its document has loaded. */
  def navigate(url: String): Task[Unit] = send("Page.navigate", obj("url" -> str(url))).flatMap(_ => loaded(200))

  private def loaded(left: Int): Task[Unit] = Task.sleep(100.millis).flatMap { _ =>
    value("return document.readyState + ' ' + location.href").flatMap { state =>
      if (state.asString.startsWith("complete") && !state.asString.contains("about:blank")) Task.unit
      else if (left == 0) Task.error(new RuntimeException("the page did not load"))
      else loaded(left - 1)
    }
  }

  /** The page as a PNG at `to`. */
  def screenshot(to: Path): Task[Unit] = send("Page.captureScreenshot").map { r =>
    Files.write(to, java.util.Base64.getDecoder.decode(r("data").asString))
  }.unit

  /** The page as a picture, in the screen's pixels. */
  def picture(): Task[java.awt.image.BufferedImage] = send("Page.captureScreenshot").map { r =>
    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder.decode(r("data").asString)))
  }

  /** The browser ended and its profile removed, where it was started here; else the socket closed. */
  def close(): Unit = {
    try socket.abort() catch { case _: Throwable => () }
    process.foreach { process =>
      process.descendants().forEach(p => { p.destroy(); () })
      process.destroy()
      if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
      process.descendants().forEach(p => { p.destroyForcibly(); () })
    }
    profile.foreach { dir =>
      try Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))
      catch { case _: Throwable => () }
    }
  }
}

object DevTools {
  /** The Chrome found on the path, where none is named. */
  def defaultChrome: String = {
    val path = sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator).toList
    List("google-chrome", "google-chrome-stable", "chromium", "chromium-browser")
      .find(n => path.exists(d => Files.isExecutable(Path.of(d, n)))).getOrElse("google-chrome")
  }

  /** The major version `binary --version` names ("Chromium 53.0.2785.0" is 53). */
  def majorOf(binary: String): Int = {
    val p = new ProcessBuilder(binary, "--version").redirectErrorStream(true).start()
    val said = new String(p.getInputStream.readAllBytes())
    p.waitFor()
    """(\d+)\.\d+\.\d+""".r.findFirstMatchIn(said).map(_.group(1).toInt)
      .getOrElse(sys.error("not a Chrome: " + binary + " --version said " + said.trim))
  }

  /** `f` with `binary` started (its user agent `agent`, where one is given), its page's console and runtime on, and the
    * browser closed after, however `f` ends. */
  def around[A](binary: String, agent: Option[String] = None)(f: DevTools => Task[A]): Task[A] =
    Task(start(binary, agent)).flatMap(tools => Task.defer(f(tools)).guarantee(Task(tools.close())))

  /** `f` with a browser already running driven where it is (a TV's: `http://<tv>:9998` on webOS 3, its inspector on),
    * its first page's console (replayed from the page's start, so nothing is lost by coming late) and runtime on, and
    * only the socket closed after. `address` is the inspector's (`http://host:port`, its first page taken) or a page's
    * own `ws://` endpoint; its major version is read from `/json/version` where it says, else `major`. */
  def attached[A](address: String, major: Int = 38)(f: DevTools => Task[A]): Task[A] =
    Task(attach(address, major)).flatMap(tools => Task.defer(f(tools)).guarantee(Task(tools.close())))

  /** A browser already running, driven where it is (`attached`); its socket closed by `close`. */
  def attach(address: String, major: Int = 38): DevTools = {
    val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    val base = address.stripSuffix("/")
    if (base.startsWith("ws")) connect(client, base, major, None, None)
    else {
      val said = try {
        val r = client.send(HttpRequest.newBuilder(URI.create(base + "/json/version")).build(),
          HttpResponse.BodyHandlers.ofString())
        val version = JsonParser(r.body())
        List("Browser", "User-Agent").flatMap(version.get).map(_.asString).flatMap(s =>
          """Chrome/(\d+)""".r.findFirstMatchIn(s).map(_.group(1).toInt)).headOption
      } catch { case _: Throwable => None }
      connect(client, page(client, base, 100), said.getOrElse(major), None, None)
    }
  }

  private def start(binary: String, agent: Option[String]): DevTools = {
    val major = majorOf(binary)
    val port = { val s = new ServerSocket(0); try s.getLocalPort finally s.close() }
    val profile = Files.createTempDirectory("everyui-chrome-")
    val flags = List("--remote-debugging-port=" + port, "--user-data-dir=" + profile, "--no-first-run",
      "--no-default-browser-check", "--password-store=basic", "--no-sandbox", "--mute-audio",
      "--disable-background-timer-throttling", "--disable-renderer-backgrounding", "--window-size=1920,1080",
      "--window-position=0,0") ++
      // an engine with no headless mode draws through its GPU process on Xvfb, which now and then stopped the page
      // answering anything (Chromium 38: about one run in two hung on some demo for good); with it off none did in ten
      (if (major >= 59) List("--headless=new", "--hide-scrollbars") else List("--disable-gpu")) ++
      agent.map("--user-agent=" + _).toList
    val builder = new ProcessBuilder(((binary :: flags) :+ "about:blank")*).redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
    builder.environment().remove("WAYLAND_DISPLAY")
    val process = builder.start()
    try {
      // (HTTP/1.1 alone: an old browser's endpoint answers no upgrade to HTTP/2)
      val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
      connect(client, page(client, "http://127.0.0.1:" + port, 100), major, Some(process), Some(profile))
    }
    catch {
      case e: Throwable =>
        process.destroyForcibly()
        throw e
    }
  }

  // the page at `endpoint` driven: its runtime and page on, and its console where the runtime does not say it
  private def connect(client: HttpClient, endpoint: String, major: Int, process: Option[Process],
                      profile: Option[Path]): DevTools = {
    val tools = new Array[DevTools](1)
    val text = new StringBuilder
    val listener = new WebSocket.Listener {
      override def onText(ws: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] = {
        text.append(data)
        if (last) {
          val whole = text.toString
          text.clear()
          tools(0).heard(JsonParser(whole))
        }
        ws.request(1)
        null
      }
    }
    val socket = client.newWebSocketBuilder().buildAsync(URI.create(endpoint), listener).get()
    tools(0) = new DevTools(process, profile, major, socket)
    val t = tools(0)
    List("Runtime.enable", "Page.enable").foreach(m => t.send(m).sync())
    if (major < 54) t.send("Console.enable").sync()
    t
  }

  // the first page's own endpoint at the inspector `base`, once it lists one
  private def page(client: HttpClient, base: String, left: Int): String = {
    val listed = try {
      val r = client.send(HttpRequest.newBuilder(URI.create(base + "/json")).build(),
        HttpResponse.BodyHandlers.ofString())
      JsonParser(r.body()).asVector.find(t => t("type").asString == "page").flatMap(_.get("webSocketDebuggerUrl"))
        .map(_.asString)
    } catch { case _: Throwable => None }
    listed.getOrElse {
      if (left == 0) sys.error("the browser opened no page to drive (" + base + ")")
      Thread.sleep(100)
      page(client, base, left - 1)
    }
  }
}
