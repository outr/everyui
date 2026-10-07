package everyui.app

import everyui.PlatformKind
import everyui.app.durable.LogSample
import everyui.log.{LogFeed, LogFilter, LogLevel, LogOptions, LogPanel, LogView}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A pipeline's log in `everyui.log.LogPanel`: a burst of lines (`lines=<n>`), then more streaming in (`stream=0`
  * stops it, `rate=<n>` lines a tick), in scribe's colours, with a warning now and then, an error and its stack
  * trace, 256 colours and colours of their own, a link, and a progress bar drawn over itself (`LogSample`).
  * `socket=ws://host:8731/logs` streams the example server's log over a durable socket instead (`LogFeed`), with each
  * line's time; `theme=light` shows the light palette; `wrap=1` wraps the lines.
  *
  * `probe=1` measures a log of `probelines=<n>` of the sample's entries (5,000; 2,000 on a Roku; a stack trace is several
  * lines) apart from the panel, each step timed, and logs it (the web, here):
  *
  *   [ui] logs probe lines=6192 make=3ms parse=32ms classify=8ms append=25ms search=7ms matches=617 filter=2ms shown=1408
  */
object LogsDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val socket = Platform.launchArg("socket")
    val view = new LogView(LogOptions(lineNumbers = true, timestamps = socket.nonEmpty,
      wrap = Platform.launchArg("wrap") == "1"))
    val panel = new LogPanel(view)
    panel.size.width := ui.size.width()
    panel.size.height := ui.size.height()
    ui.children += panel
    if (socket.nonEmpty) LogFeed.connect(view, socket, "demo")
    else stream(view)
    if (Platform.launchArg("probe") == "1") Platform.after(0.5, () => probe())
    Platform.later(() => panel.focus())
    // a frozen frame (tools/check.sh, `--freeze`), once the burst is drawn
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }

  private def number(name: String, otherwise: Int): Int = {
    val v = Platform.launchArg(name)
    if (v.isEmpty) otherwise else v.toInt
  }

  private def roku: Boolean = Platform.kind == PlatformKind.Roku

  // the next piece of the stream, and how many a tick (kept here: a Roku's closures copy what they capture)
  private var next = 0
  private var rate = 2

  // a burst, then a few lines a tick
  private def stream(view: LogView): Unit = {
    val burst = number("lines", if (roku) 300 else 3000)
    val began = Platform.ticks()
    while (next < burst) {
      view.append(LogSample.chunk(next))
      next += 1
    }
    view.settle()
    Platform.log("logs burst lines=" + burst.toString + " took=" + (Platform.ticks() - began).toInt.toString + "ms")
    rate = number("rate", 2)
    if (Platform.launchArg("stream") != "0") Platform.after(0.25, () => tick(view))
  }

  private def tick(view: LogView): Unit = if (!view.takenApart) {
    var i = 0
    while (i < rate) {
      view.append(LogSample.chunk(next))
      next += 1
      i += 1
    }
    Platform.after(0.25, () => tick(view))
  }

  // a log apart from the panel, filled, searched and filtered, each timed
  private def probe(): Unit = {
    val lines = number("probelines", if (roku) 2000 else 5000)
    val log = new LogView(LogOptions(lineNumbers = true))
    // (no room for rows: what is measured is the log, not the drawing of it)
    log.size.height @= 0.0
    // the lines made, read, classified, then added: each timed
    var at = Platform.ticks()
    // (an array: a Roku's lists copy themselves to grow)
    val texts = new Array[String](lines)
    var n = 0
    while (n < lines) {
      texts(n) = LogSample.line(n) + "\n"
      n += 1
    }
    val make = Platform.ticks() - at
    at = Platform.ticks()
    val parser = new everyui.log.AnsiParser
    val read = new Array[String](lines)
    n = 0
    while (n < lines) {
      val raws = parser.lines(texts(n))
      var k = 0
      while (k < raws.length) {
        val l = parser.line(raws(k))
        if (k == 0) read(n) = l.text
        k += 1
      }
      n += 1
    }
    val parse = Platform.ticks() - at
    at = Platform.ticks()
    read.foreach(t => everyui.log.LogClassifier.classify(t))
    val classify = Platform.ticks() - at
    at = Platform.ticks()
    texts.foreach(t => log.append(t))
    log.settle()
    val appended = Platform.ticks() - at
    val searched = Platform.ticks()
    log.search @= "timed out"
    def done(): Unit = if (log.searching()) Platform.later(() => done()) else {
      val search = Platform.ticks() - searched
      val found = log.matches()
      val filtered = Platform.ticks()
      log.filter @= LogFilter(levels = Set(LogLevel.Warn, LogLevel.Error))
      val filter = Platform.ticks() - filtered
      Platform.log("logs probe lines=" + log.count().toString + " make=" + make.toInt.toString + "ms parse=" +
        parse.toInt.toString + "ms classify=" + classify.toInt.toString + "ms append=" + appended.toInt.toString +
        "ms search=" + search.toInt.toString + "ms matches=" + found.toString + " filter=" + filter.toInt.toString +
        "ms shown=" + (log.count() - log.hidden()).toString)
      log.dispose()
    }
    Platform.later(() => done())
  }
}
