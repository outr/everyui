package everyui.log

/** Text written for a terminal read into lines a log view can show: its colours and attributes kept as spans
  * (`AnsiLine`), everything else a terminal would act on taken out.
  *
  * It reads the SGR codes: reset, bold, dim, italic, underline, strikethrough, reverse and conceal, and each one's
  * undoing; the sixteen colours and their bright forms; xterm's 256 (`38;5;n`, `48;5;n`) and colours of their own
  * (`38;2;r;g;b`, and the `:` forms); and the default colours (39, 49). The style carries on from line to line, as a
  * terminal's does. Anything else is dropped cleanly: the cursor's moves and the other CSI sequences (a private
  * marker's among them), OSC ended by BEL or ST (an OSC 8 hyperlink becomes a link), the other escapes, and the
  * control characters. A `\r` with more after it starts the line again, so a progress bar drawn over itself shows
  * its last state; tabs are set out to every eighth column.
  *
  * Text comes in pieces (`feed`): a line is read once its newline has come, so an escape cut between two pieces is
  * whole by then. A line that never ends is cut at `AnsiParser.longest` characters.
  *
  * {{{
  *   val parser = new AnsiParser
  *   parser.feed("\u001b[32mok\u001b[0m done\nhalf") // one line: "ok done", "ok" in green
  *   parser.feed(" a line\n")                         // "half a line"
  * }}} */
class AnsiParser {
  private var held: String = ""
  private var style: AnsiStyle = AnsiStyle.Plain
  private var href: String = ""

  /** The lines `chunk` ends, read; what follows the last newline is held for the next. */
  def feed(chunk: String): List[AnsiLine] = lines(chunk).toList.map(raw => line(raw))

  /** The lines `chunk` ends as they were written, in order, for `line` to read (a `LogView` reads a stretch of them a
    * turn); what follows the last newline is held for the next. An array, filled in place: a Roku's lists copy
    * themselves to grow. */
  def lines(chunk: String): Array[String] = {
    val text = held + chunk
    var count = 0
    var nl = text.indexOf("\n")
    while (nl >= 0) {
      count += 1
      nl = text.indexOf("\n", nl + 1)
    }
    val out = new Array[String](count + 1)
    var from = 0
    var i = 0
    while (i < count) {
      nl = text.indexOf("\n", from)
      out(i) = text.substring(from, nl)
      from = nl + 1
      i += 1
    }
    held = text.substring(from)
    trim() match {
      case Some(cut) =>
        out(count) = cut
        out
      case None => out.take(count)
    }
  }

  /** The line held, read as it is: what is left once a stream has ended. */
  def flush(): List[AnsiLine] = rest().map(l => line(l))

  /** The line held, as it was written, let go. */
  def rest(): List[String] = if (held.isEmpty) Nil else {
    val last = held
    held = ""
    List(last)
  }

  /** What is held of a line not ended yet. */
  def pending: String = held

  /** Back to the start: nothing held, the style plain. */
  def reset(): Unit = {
    held = ""
    style = AnsiStyle.Plain
    href = ""
  }

  /** One whole line read, its escapes applied: only what follows its last `\r` is kept, though what came before it
    * still sets the style. */
  def line(raw: String): AnsiLine = {
    val whole = if (raw.endsWith("\r")) raw.substring(0, raw.length - 1) else raw
    val cr = whole.lastIndexOf("\r")
    if (cr < 0) read(whole, true)
    else {
      read(whole.substring(0, cr), false)
      read(whole.substring(cr + 1), true)
    }
  }

  // a line held too long: what a `\r` started over is let go (only what follows it shows, though its escapes go
  // with it); failing that, it is cut
  private def trim(): Option[String] =
    if (held.length <= AnsiParser.longest) None
    else {
      val cr = held.lastIndexOf("\r")
      if (cr > 0 && held.length - cr <= AnsiParser.longest) {
        held = held.substring(cr)
        None
      } else {
        val cut = held.substring(0, AnsiParser.longest)
        held = held.substring(AnsiParser.longest)
        Some(cut)
      }
    }

  // `s` read: its words kept where `keep` says, its escapes applied as they come
  private def read(s: String, keep: Boolean): AnsiLine = {
    val eightBit = s.indexOf("\u009b") >= 0 || s.indexOf("\u009d") >= 0
    // (looked for once a line: most have no tab and no control character)
    val tabbed = s.indexOf("\t") >= 0
    val dirty = AnsiParser.controlled(s)
    var text = ""
    var spans: List[LogSpan] = Nil
    var runStart = 0
    var runStyle = style
    var runHref = href
    var i = 0
    while (i < s.length) {
      val esc = if (eightBit) AnsiParser.first(s, i) else s.indexOf("\u001b", i)
      val end = if (esc < 0) s.length else esc
      if (keep && end > i) {
        val words = s.substring(i, end)
        text = text + (if (tabbed || dirty) AnsiParser.clean(words, text.length) else words)
      }
      if (esc < 0) i = s.length
      else {
        i = escape(s, esc)
        if (keep && (!(style eq runStyle) && style != runStyle || href != runHref)) {
          spans = AnsiParser.close(spans, runStart, text.length, runStyle, runHref)
          runStart = text.length
          runStyle = style
          runHref = href
        }
      }
    }
    if (keep) spans = AnsiParser.close(spans, runStart, text.length, runStyle, runHref)
    AnsiLine(text, spans.reverse)
  }

  // the escape at `at` applied, and where what follows it starts
  private def escape(s: String, at: Int): Int = {
    val c = s.charAt(at)
    if (c == '\u009b') csi(s, at + 1)
    else if (c == '\u009d') osc(s, at + 1)
    else if (at + 1 >= s.length) s.length
    else {
      val k = s.charAt(at + 1)
      if (k == '[') csi(s, at + 2)
      else if (k == ']') osc(s, at + 2)
      // a device control, start or privacy message or application command: everything to its terminator
      else if (k == 'P' || k == 'X' || k == '^' || k == '_') {
        val end = AnsiParser.terminator(s, at + 2)
        if (end < 0) s.length else AnsiParser.after(s, end)
      }
      // a character set named (ESC ( B), a line's size (ESC # 8): one more character
      else if (k == '(' || k == ')' || k == '*' || k == '+' || k == '-' || k == '.' || k == '/' || k == '#' ||
        k == '%' || k == ' ') math.min(s.length, at + 3)
      // the two-character escapes: the cursor saved and restored (ESC 7, ESC 8), the keypad's mode, a reset
      else if (k >= '0' && k <= '~') at + 2
      // an escape before anything else is dropped on its own
      else at + 1
    }
  }

  // a control sequence: its parameters, intermediates and final; only SGR (`m`, no private marker) does anything
  private def csi(s: String, from: Int): Int = {
    var i = from
    while (i < s.length && s.charAt(i) >= '0' && s.charAt(i) <= '?') i += 1
    val params = s.substring(from, i)
    val mid = i
    while (i < s.length && s.charAt(i) >= ' ' && s.charAt(i) <= '/') i += 1
    if (i >= s.length) s.length
    else {
      val f = s.charAt(i)
      if (f >= '@' && f <= '~') {
        if (f == 'm' && mid == i && (params.isEmpty || params.charAt(0) <= ';')) sgr(params)
        i + 1
      }
      // broken off by something that cannot be in one: that is text again
      else i
    }
  }

  // an operating system command: a link where it is OSC 8, else nothing
  private def osc(s: String, from: Int): Int = {
    val end = AnsiParser.terminator(s, from)
    if (end < 0) s.length
    else {
      val body = s.substring(from, end)
      if (body.startsWith("8;")) {
        val semi = body.indexOf(";", 2)
        href = if (semi < 0) "" else body.substring(semi + 1)
      }
      AnsiParser.after(s, end)
    }
  }

  // Select Graphic Rendition: `params` as written, numbers split by ';', and by ':' where one has its own parameters
  private def sgr(params: String): Unit =
    if (params.isEmpty) style = AnsiStyle.Plain
    // the usual: one code (a reset, a colour, bold), read without the general form's arrays
    else if (params.indexOf(";") < 0 && params.indexOf(":") < 0) single(AnsiParser.number(params))
    else codes(params)

  private def codes(params: String): Unit = {
    val values = new Array[Int](params.length + 1)
    val joined = new Array[Boolean](params.length + 1)
    var n = 0
    var value = -1
    var i = 0
    while (i <= params.length) {
      if (i == params.length || params.charAt(i) == ';' || params.charAt(i) == ':') {
        values(n) = value
        n += 1
        value = -1
        if (i < params.length) joined(n) = params.charAt(i) == ':'
      } else {
        val c = params.charAt(i)
        if (c >= '0' && c <= '9') value = (if (value < 0) 0 else value) * 10 + (c - '0')
      }
      i += 1
    }
    i = 0
    while (i < n) {
      val code = values(i)
      var subs = i + 1
      while (subs < n && joined(subs)) subs += 1
      if (subs > i + 1) {
        // 38:5:n, 38:2::r:g:b (a colour space or not), 4:3
        if (code == 4) style = style.copy(underline = values(i + 1) > 0)
        else if (code == 38 || code == 48) colour(code, values, i + 1, subs, true)
        i = subs
      }
      else if (code == 38 || code == 48) i = colour(code, values, i + 1, n, false)
      else {
        single(code)
        i += 1
      }
    }
  }

  // an extended colour from `values` at `from` (to `until`): 5 then an index, or 2 then red, green and blue; where the
  // reading carries on from
  private def colour(code: Int, values: Array[Int], from: Int, until: Int, sub: Boolean): Int =
    if (from >= until) until
    else if (values(from) == 5 && from + 1 < until) {
      set(code, math.max(0, math.min(255, values(from + 1))))
      from + 2
    }
    else if (values(from) == 2) {
      // the sub-parameter form may name a colour space first (38:2::r:g:b): the last three are the colour
      val r = if (sub) until - 3 else from + 1
      if (r > from && r + 2 < until) set(code, AnsiStyle.rgb(values(r), values(r + 1), values(r + 2)))
      if (sub) until else math.min(until, from + 4)
    }
    else from + 1

  private def set(code: Int, c: Int): Unit =
    style = if (code == 38) style.copy(fg = c) else style.copy(bg = c)

  private def single(code: Int): Unit =
    if (code <= 0) style = AnsiStyle.Plain
    else if (code == 1) style = style.copy(bold = true)
    else if (code == 2) style = style.copy(dim = true)
    else if (code == 3) style = style.copy(italic = true)
    else if (code == 4 || code == 21) style = style.copy(underline = true)
    else if (code == 7) style = style.copy(reverse = true)
    else if (code == 8) style = style.copy(conceal = true)
    else if (code == 9) style = style.copy(strike = true)
    else if (code == 22) style = style.copy(bold = false, dim = false)
    else if (code == 23) style = style.copy(italic = false)
    else if (code == 24) style = style.copy(underline = false)
    else if (code == 27) style = style.copy(reverse = false)
    else if (code == 28) style = style.copy(conceal = false)
    else if (code == 29) style = style.copy(strike = false)
    else if (code >= 30 && code <= 37) style = style.copy(fg = code - 30)
    else if (code == 39) style = style.copy(fg = AnsiStyle.Default)
    else if (code >= 40 && code <= 47) style = style.copy(bg = code - 40)
    else if (code == 49) style = style.copy(bg = AnsiStyle.Default)
    else if (code >= 90 && code <= 97) style = style.copy(fg = code - 82)
    else if (code >= 100 && code <= 107) style = style.copy(bg = code - 92)
}

object AnsiParser {
  /** The longest a line may be before it is cut, in characters. */
  val longest: Int = 65536

  /** `text` with every escape and control character taken out: a line's words as they read. */
  def strip(text: String): String = new AnsiParser().line(text).text

  // the control characters taken out of the words (tabs are set out, and the escapes read, before)
  private val controls: List[String] = List("\u0007", "\b", "\u000b", "\u000c", "\u000e", "\u000f", "\u007f",
    "\u009c")

  // the number `digits` writes (anything else in it skipped), -1 for none
  private def number(digits: String): Int = {
    var value = -1
    var i = 0
    while (i < digits.length) {
      val c = digits.charAt(i)
      if (c >= '0' && c <= '9') value = (if (value < 0) 0 else value) * 10 + (c - '0')
      i += 1
    }
    value
  }

  // whether `s` holds a control character to take out
  private def controlled(s: String): Boolean = controls.exists(c => s.indexOf(c) >= 0)

  // the first escape at or after `from`: ESC, or an 8-bit CSI or OSC
  private def first(s: String, from: Int): Int = {
    var best = s.indexOf("\u001b", from)
    val csi = s.indexOf("\u009b", from)
    if (csi >= 0 && (best < 0 || csi < best)) best = csi
    val osc = s.indexOf("\u009d", from)
    if (osc >= 0 && (best < 0 || osc < best)) best = osc
    best
  }

  // where a string started at `from` ends: BEL, ST (ESC \) or the 8-bit ST; -1 where it does not on this line
  private def terminator(s: String, from: Int): Int = {
    var best = s.indexOf("\u0007", from)
    val st = s.indexOf("\u001b\\", from)
    if (st >= 0 && (best < 0 || st < best)) best = st
    val c1 = s.indexOf("\u009c", from)
    if (c1 >= 0 && (best < 0 || c1 < best)) best = c1
    best
  }

  // just after the terminator at `end`
  private def after(s: String, end: Int): Int = if (s.charAt(end) == '\u001b') end + 2 else end + 1

  // words between escapes: tabs set out from column `col`, the control characters taken out
  private def clean(words: String, col: Int): String = {
    var t = if (words.indexOf("\t") < 0) words else tabs(words, col)
    controls.foreach(c => if (t.indexOf(c) >= 0) t = t.replace(c, ""))
    t
  }

  private def tabs(words: String, col: Int): String = {
    var out = ""
    var from = 0
    var tab = words.indexOf("\t")
    while (tab >= 0) {
      out = out + words.substring(from, tab)
      out = out + " " * (8 - (col + out.length) % 8)
      from = tab + 1
      tab = words.indexOf("\t", from)
    }
    out + words.substring(from)
  }

  // the run from `from` to `to` added to `spans` (newest first), or joined to the one before it where it is the same
  private def close(spans: List[LogSpan], from: Int, to: Int, style: AnsiStyle, href: String): List[LogSpan] =
    if (to <= from || (style.plain && href.isEmpty)) spans
    else spans match {
      case last :: rest if last.to == from && last.style == style && last.href == href =>
        last.copy(to = to) :: rest
      case _ => LogSpan(from, to, style, href) :: spans
    }
}
