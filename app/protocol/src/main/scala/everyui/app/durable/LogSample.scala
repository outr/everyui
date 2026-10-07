package everyui.app.durable

/** A pipeline's log as it writes it, for demo=logs and the example server's `/logs`, the same each time: scribe's
  * layouts in their colours (the classic one line, the advanced two), debugging and tracing, a warning, an error with
  * its stack trace, a table set out with tabs, xterm's 256 colours and colours of their own, a link (OSC 8), and a
  * download whose progress bar is drawn over itself with `\r`. */
object LogSample {
  private val esc = "\u001b["
  private val reset = esc + "0m"
  private val states = List("louisiana", "colorado", "wyoming", "utah", "montana", "ohio")
  private val threads = List("main", "pipeline-1", "pipeline-2", "pipeline-3", "io-4")
  private val places = List("dw.Loader.load:142", "dw.StateRunner.task:87", "dw.Wells.parse:311", "dw.Upload.send:64",
    "dw.Geo.project:29")

  /** The stream's `n`th piece (from 0), as a terminal would be sent it: whole lines with their newlines, or a step of
    * the progress bar, which ends its line only once it is done. */
  def chunk(n: Int): String = {
    val step = n % 60
    if (step >= 40 && step <= 50) {
      val done = (step - 40) * 10
      val bar = "#" * (done / 5) + "-" * (20 - done / 5)
      "\r" + esc + "36m" + "Downloading " + states(n / 60 % states.length) + "-wells.csv " + reset + "[" + esc + "32m" +
        bar + reset + "] " + done.toString + "%" + (if (done >= 100) " done\n" else "")
    }
    else line(n) + "\n"
  }

  /** The `n`th line (more than one for a stack trace), with no progress bar: what the server sends as an event. */
  def line(n: Int): String = {
    val state = states(n % states.length)
    val count = 300 + (n * 7919) % 9000
    if (n == 0) gradient("EveryUI log demo: a pipeline's run, streamed")
    else if (n % 41 == 0) error(n) + "\n" + trace(n)
    else if (n % 13 == 0) classic(n, "WARN", "Retrying " + state + " page " + (n % 9 + 1).toString +
      " (attempt 2/5): read timed out after 30s")
    else if (n % 23 == 0) palette(n)
    else if (n % 31 == 0) gradient("Projected " + count.toString + " wells onto NAD83 for " + state)
    else if (n % 37 == 0) classic(n, "INFO", "See " + "\u001b]8;;https://everyui.com/docs/logs\u001b\\" +
      esc + "4mthe log docs" + reset + "\u001b]8;;\u001b\\ for the format")
    else if (n % 19 == 0) "rows\t" + count.toString + "\tskipped\t" + (n % 5).toString + "\tstate\t" + state
    else if (n % 11 == 0) advanced(n, "INFO") + "\n" + "Wrote " + count.toString + " rows of " + state +
      " to the deliverable"
    else if (n % 7 == 0) classic(n, "DEBUG", "Parsed " + state + " record " + (n * 31).toString + ": " +
      "{\"api\":\"" + (42000000 + n).toString + "\",\"operator\":\"Acme Energy\",\"status\":\"Active\"}")
    else if (n % 17 == 0) classic(n, "TRACE", "cache hit " + state + "/" + (n % 97).toString)
    else classic(n, "INFO", "Loaded " + count.toString + " wells from " + state + " (page " + (n % 12 + 1).toString + ")")
  }

  /** The level `line(n)` is written at, by name ("" for none). */
  def level(n: Int): String =
    if (n == 0 || n % 23 == 0 || n % 31 == 0 || n % 19 == 0) ""
    else if (n % 41 == 0) "ERROR"
    else if (n % 13 == 0) "WARN"
    else if (n % 37 == 0 || n % 11 == 0) "INFO"
    else if (n % 7 == 0) "DEBUG"
    else if (n % 17 == 0) "TRACE"
    else "INFO"

  // scribe's classic layout, coloured as its compact one is
  private def classic(n: Int, level: String, message: String): String =
    esc + "36;1m" + date(n) + reset + " [" + esc + "3m" + pad(threads(n % threads.length), 10) + reset + "] " +
      colour(level) + level + reset + " " + esc + "32m" + places(n % places.length) + reset + " - " + message

  // scribe's advanced layout: the head of a record, its message on the next line
  private def advanced(n: Int, level: String): String =
    esc + "36;1m" + date(n) + reset + " " + esc + "3m" + threads(n % threads.length) + reset + " " + colour(level) +
      level + reset + " " + esc + "32m" + places(n % places.length) + reset

  private def error(n: Int): String =
    classic(n, "ERROR", "Upload of " + states(n % states.length) + " failed: " + esc + "1mjava.net.SocketTimeoutException" +
      reset + ": Read timed out")

  private def trace(n: Int): String = List(
    "java.net.SocketTimeoutException: Read timed out",
    "\tat java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:278)",
    "\tat java.base/java.net.Socket$SocketInputStream.read(Socket.java:1099)",
    "\tat spice.http.client.JVMHttpClientImplementation.send(JVMHttpClientImplementation.scala:" + (90 + n % 40).toString + ")",
    "\tat dw.Upload.send(Upload.scala:64)",
    "Caused by: " + esc + "31mjava.io.IOException" + reset + ": connection reset by peer",
    "\t... 12 more").mkString("\n")

  // the 256 colours' cube and greys, a few of each
  private def palette(n: Int): String = {
    var out = "palette "
    var i = 0
    while (i < 24) {
      val c = if (i < 18) 16 + (n * 5 + i * 12) % 216 else 232 + (i - 18) * 4
      out = out + esc + "48;5;" + c.toString + "m  " + reset
      i += 1
    }
    out + " " + esc + "38;5;208mxterm 208" + reset + " " + esc + "38;5;33;1mbold 33" + reset
  }

  // words in colours of their own, a gradient letter by letter
  private def gradient(words: String): String = {
    var out = ""
    var i = 0
    while (i < words.length) {
      val t = i * 255 / math.max(1, words.length - 1)
      out = out + esc + "38;2;" + (255 - t / 2).toString + ";" + (90 + t / 3).toString + ";" + t.toString + "m" +
        words.substring(i, i + 1)
      i += 1
    }
    out + reset
  }

  private def colour(level: String): String = level match {
    case "ERROR" => esc + "31m"
    case "WARN" => esc + "33m"
    case "INFO" => esc + "34m"
    case "DEBUG" => esc + "32m"
    case _ => esc + "37m"
  }

  private def date(n: Int): String = {
    val seconds = 6 * 3600 + n * 3 / 2
    "2026.10.07 " + two(seconds / 3600 % 24) + ":" + two(seconds / 60 % 60) + ":" + two(seconds % 60)
  }

  private def two(v: Int): String = if (v < 10) "0" + v.toString else v.toString

  private def pad(s: String, width: Int): String = s + " " * math.max(0, width - s.length)
}
