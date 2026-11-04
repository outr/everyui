package everyui.markdown

/** Markdown's inline syntax (CommonMark's, and GFM's strikethrough and bare links): the words of a paragraph, a
  * heading or a table cell as `MdInline`s. Emphasis follows CommonMark's delimiter runs, a stack of them resolved
  * from each closer back to its nearest opener, so `*a **b** c*` and `**a*` come out as the spec has them.
  *
  * Written with a parser's state in fields and loops over indices rather than local functions, which the Roku's
  * compiler does not take. */
private[markdown] class Inlines(src: String, refs: Map[String, MdReference]) {
  private var first: Piece = null
  private var last: Piece = null
  private var delimiters: Delimiter = null
  private var brackets: Bracket = null
  // the words since the last piece, newest first, and where a stretch of them still being read began (-1 for none):
  // plain words are taken from the source a stretch at a time, rather than a character at a time
  private var words: List[String] = Nil
  private var from = -1
  private var pos = 0

  /** The pieces `src` is made of. */
  def parse(): List[MdInline] = {
    while (pos < src.length) step()
    flush()
    emphasis(null)
    collect(first, null)
  }

  private def step(): Unit = {
    val c = src.charAt(pos)
    val special = c == '\\' || c == '`' || c == '*' || c == '_' || c == '~' || c == '[' || c == ']' || c == '<' ||
      c == '&' || c == '\n' || c == '!' || c == 'h' || c == 'w'
    if (special) settle()
    if (!special) {
      if (from < 0) from = pos
      pos += 1
    }
    else if (c == '\\') escape()
    else if (c == '`') code()
    else if (c == '*' || c == '_' || c == '~') run(c)
    else if (c == '[') open(false, 1)
    else if (c == '!' && pos + 1 < src.length && src.charAt(pos + 1) == '[') open(true, 2)
    else if (c == ']') close()
    else if (c == '<') angled()
    else if (c == '&') entity()
    else if (c == '\n') lineEnd(false)
    else if ((c == 'h' || c == 'w') && bare()) ()
    else {
      from = pos
      pos += 1
    }
  }

  /** The stretch of plain words being read, taken into `words`. */
  private def settle(): Unit = if (from >= 0) {
    words = src.substring(from, pos) :: words
    from = -1
  }

  /** `s` added to the words. */
  private def say(s: String): Unit = {
    settle()
    words = s :: words
  }

  // ---- the list of pieces ----

  private def add(inline: MdInline): Piece = {
    val p = new Piece(inline)
    if (last == null) first = p
    else {
      last.next = p
      p.prev = last
    }
    last = p
    p
  }

  /** The words gathered so far, as a piece of text. */
  private def flush(): Unit = {
    settle()
    val text = words.reverse.mkString
    if (text.nonEmpty) add(MdInline.Text(text))
    words = Nil
  }

  private def remove(p: Piece): Unit = {
    if (p.prev == null) first = p.next else p.prev.next = p.next
    if (p.next == null) last = p.prev else p.next.prev = p.prev
  }

  /** The pieces from `from` up to (not including) `until`, merged where two runs of text meet. */
  private def collect(from: Piece, until: Piece): List[MdInline] = {
    var out: List[MdInline] = Nil
    var p = from
    while (p != null && (p ne until)) {
      out = (p.inline, out.headOption) match {
        case (MdInline.Text(b), Some(MdInline.Text(a))) => MdInline.Text(a + b) :: out.tail
        case _ => p.inline :: out
      }
      p = p.next
    }
    out.reverse
  }

  // ---- escapes, entities, line ends ----

  private def escape(): Unit = {
    val next = if (pos + 1 < src.length) src.charAt(pos + 1) else ' '
    if (next == '\n') {
      pos += 1
      lineEnd(true)
    }
    else if (Inlines.punctuation(next)) {
      say(next.toString)
      pos += 2
    }
    else {
      say("\\")
      pos += 1
    }
  }

  private def entity(): Unit = {
    val (text, length) = Entities.at(src, pos)
    if (length > 0) {
      say(text)
      pos += length
    }
    else {
      say("&")
      pos += 1
    }
  }

  /** A line's end inside the paragraph: a hard break after two spaces (or a backslash, `hard`), else a soft one; the
    * next line's leading spaces go. */
  private def lineEnd(hard: Boolean): Unit = {
    settle()
    val text = words.reverse.mkString
    var spaces = 0
    while (spaces < text.length && text.charAt(text.length - 1 - spaces) == ' ') spaces += 1
    words = List(text.substring(0, text.length - spaces))
    flush()
    add(if (hard || spaces >= 2) MdInline.HardBreak else MdInline.SoftBreak)
    pos += 1
    while (pos < src.length && src.charAt(pos) == ' ') pos += 1
  }

  // ---- code ----

  private def code(): Unit = {
    val ticks = runLength(pos, '`')
    // the next run of exactly as many backticks closes it
    var at = pos + ticks
    var end = -1
    while (end < 0 && at < src.length) {
      val found = src.indexOf("`", at)
      if (found < 0) at = src.length
      else {
        val length = runLength(found, '`')
        if (length == ticks) end = found else at = found + length
      }
    }
    if (end < 0) {
      say(src.substring(pos, pos + ticks))
      pos += ticks
    }
    else {
      var body = src.substring(pos + ticks, end).replace('\n', ' ')
      if (body.length >= 2 && body.charAt(0) == ' ' && body.charAt(body.length - 1) == ' ' && body.trim.nonEmpty)
        body = body.substring(1, body.length - 1)
      flush()
      add(MdInline.CodeSpan(body))
      pos = end + ticks
    }
  }

  private def runLength(from: Int, c: Char): Int = {
    var k = from
    while (k < src.length && src.charAt(k) == c) k += 1
    k - from
  }

  // ---- emphasis ----

  /** A run of `*`, `_` or `~`: text, and a delimiter where it can open or close something. */
  private def run(c: Char): Unit = {
    val length = runLength(pos, c)
    val before = if (pos == 0) ' ' else src.charAt(pos - 1)
    val after = if (pos + length >= src.length) ' ' else src.charAt(pos + length)
    val left = !Inlines.space(after) && (!Inlines.punctuation(after) || Inlines.space(before) || Inlines.punctuation(before))
    val right = !Inlines.space(before) && (!Inlines.punctuation(before) || Inlines.space(after) || Inlines.punctuation(after))
    val canOpen = if (c == '_') left && (!right || Inlines.punctuation(before)) else left
    val canClose = if (c == '_') right && (!left || Inlines.punctuation(after)) else right
    flush()
    val piece = add(MdInline.Text(src.substring(pos, pos + length)))
    // strikethrough is one or two tildes; a longer run is only tildes
    if ((canOpen || canClose) && (c != '~' || length <= 2)) {
      val d = new Delimiter(piece, c, length, canOpen, canClose)
      d.prev = delimiters
      if (delimiters != null) delimiters.next = d
      delimiters = d
    }
    pos += length
  }

  private def unstack(d: Delimiter): Unit = {
    if (d.prev != null) d.prev.next = d.next
    if (d.next != null) d.next.prev = d.prev
    else delimiters = d.prev
  }

  /** Match the delimiters above `bottom` into emphasis, strong emphasis and strikethrough, as CommonMark's "process
    * emphasis" does, then take them all off the stack. */
  private def emphasis(bottom: Delimiter): Unit = {
    var closer = delimiters
    if (closer eq bottom) closer = null
    else while (closer.prev != null && (closer.prev ne bottom)) closer = closer.prev
    // the lowest an opener may be for each kind of closer, once a search for one has failed
    var floors: Map[String, Delimiter] = Map.empty
    while (closer != null) {
      if (!closer.canClose) closer = closer.next
      else {
        val kind = closer.char.toString + (closer.original % 3).toString + closer.canOpen.toString
        val floor = floors.getOrElse(kind, bottom)
        var opener = closer.prev
        var found: Delimiter = null
        while (found == null && opener != null && (opener ne bottom) && (opener ne floor)) {
          if (matches(opener, closer)) found = opener else opener = opener.prev
        }
        if (found == null) {
          floors = floors + (kind -> closer.prev)
          val next = closer.next
          if (!closer.canOpen) unstack(closer)
          closer = next
        }
        else closer = wrap(found, closer)
      }
    }
    while (delimiters != null && (delimiters ne bottom)) unstack(delimiters)
  }

  private def matches(opener: Delimiter, closer: Delimiter): Boolean =
    if (opener.char != closer.char || !opener.canOpen) false
    else if (closer.char == '~') opener.count == closer.count
    // the rule of three: a run that can both open and close does not pair with one that sums with it to a multiple of 3
    else !((opener.canClose || closer.canOpen) && (opener.original + closer.original) % 3 == 0 &&
      !(opener.original % 3 == 0 && closer.original % 3 == 0))

  /** The pieces between `opener` and `closer` taken into emphasis (or strikethrough), what it used taken off both;
    * the closer to go on from. */
  private def wrap(opener: Delimiter, closer: Delimiter): Delimiter = {
    val used = if (closer.char == '~') closer.count else if (opener.count >= 2 && closer.count >= 2) 2 else 1
    val inside = collect(opener.piece.next, closer.piece)
    val made =
      if (closer.char == '~') MdInline.Strike(inside)
      else if (used == 2) MdInline.Strong(inside)
      else MdInline.Emphasis(inside)
    // lift the pieces out, and put the emphasis in their place
    val node = new Piece(made)
    node.prev = opener.piece
    node.next = closer.piece
    opener.piece.next = node
    closer.piece.prev = node
    // the delimiters between them are spent
    var d = closer.prev
    while (d != null && (d ne opener)) {
      val before = d.prev
      unstack(d)
      d = before
    }
    opener.count -= used
    closer.count -= used
    opener.piece.inline = MdInline.Text(opener.piece.inline match {
      case MdInline.Text(t) => t.substring(used)
      case _ => ""
    })
    closer.piece.inline = MdInline.Text(closer.piece.inline match {
      case MdInline.Text(t) => t.substring(used)
      case _ => ""
    })
    if (opener.count == 0) {
      remove(opener.piece)
      unstack(opener)
    }
    if (closer.count == 0) {
      remove(closer.piece)
      val next = closer.next
      unstack(closer)
      next
    }
    else closer
  }

  // ---- links and images ----

  private def open(image: Boolean, length: Int): Unit = {
    flush()
    val piece = add(MdInline.Text(src.substring(pos, pos + length)))
    brackets = new Bracket(piece, image, pos + length, delimiters, brackets)
    pos += length
  }

  private def close(): Unit = {
    val opener = brackets
    if (opener == null) {
      say("]")
      pos += 1
    }
    else if (!opener.active) {
      brackets = opener.before
      say("]")
      pos += 1
    }
    else {
      val label = src.substring(opener.from, pos)
      val found = inlineLink(pos + 1)
      var href = ""
      var title = ""
      var end = -1
      if (found.nonEmpty) {
        href = found.get.href
        title = found.get.title
        end = found.get.end
      }
      else {
        // a reference: [text][label], [text][] or [text]
        val full = if (pos + 1 < src.length && src.charAt(pos + 1) == '[') src.indexOf("]", pos + 2) else -1
        val named = if (full < 0) label else src.substring(pos + 2, full)
        val key = Inlines.normalise(if (named.trim.isEmpty) label else named)
        val r = if (key.isEmpty) None else refs.get(key)
        if (r.nonEmpty) {
          href = r.get.href
          title = r.get.title
          end = if (full < 0) pos + 1 else full + 1
        }
      }
      brackets = opener.before
      if (end < 0) {
        say("]")
        pos += 1
      }
      else {
        flush()
        emphasis(opener.bottom)
        val inside = collect(opener.piece.next, null)
        val made =
          if (opener.image) MdInline.Picture(href, title, MdInline.plain(inside))
          else MdInline.Hyperlink(href, title, inside)
        // the bracket and everything after it become the link
        while (last != null && (last ne opener.piece)) remove(last)
        opener.piece.inline = made
        // no link inside a link: the brackets before this one open nothing now
        if (!opener.image) {
          var b = brackets
          while (b != null) {
            if (!b.image) b.active = false
            b = b.before
          }
        }
        pos = end
      }
    }
  }

  /** `(href "title")` at `at`, where there is one. */
  private def inlineLink(at: Int): Option[LinkTarget] =
    if (at >= src.length || src.charAt(at) != '(') None
    else {
      var k = skipSpace(at + 1)
      var href = ""
      var ok = true
      if (k < src.length && src.charAt(k) == '<') {
        val end = src.indexOf(">", k)
        val nl = src.indexOf("\n", k)
        if (end < 0 || (nl >= 0 && nl < end)) ok = false
        else {
          href = src.substring(k + 1, end)
          k = end + 1
        }
      }
      else {
        // up to a space or the unbalanced `)`
        val from = k
        var depth = 0
        var going = true
        while (going && k < src.length) {
          val c = src.charAt(k)
          if (c == '\\' && k + 1 < src.length) k += 2
          else if (c == ' ' || c == '\n' || c < ' ') going = false
          else if (c == '(') {
            depth += 1
            k += 1
          }
          else if (c == ')') {
            if (depth == 0) going = false
            else {
              depth -= 1
              k += 1
            }
          }
          else k += 1
        }
        href = src.substring(from, k)
      }
      val spaced = k
      k = skipSpace(k)
      var title = ""
      if (ok && k < src.length && k > spaced && (src.charAt(k) == '"' || src.charAt(k) == '\'' || src.charAt(k) == '(')) {
        val shut = if (src.charAt(k) == '(') ')' else src.charAt(k)
        var e = k + 1
        while (e < src.length && src.charAt(e) != shut) e += (if (src.charAt(e) == '\\') 2 else 1)
        if (e >= src.length) ok = false
        else {
          title = src.substring(k + 1, e)
          k = skipSpace(e + 1)
        }
      }
      if (!ok || k >= src.length || src.charAt(k) != ')') None
      else Some(LinkTarget(Inlines.unescape(Entities.decode(href)), Inlines.unescape(Entities.decode(title)), k + 1))
    }

  private def skipSpace(from: Int): Int = {
    var k = from
    while (k < src.length && (src.charAt(k) == ' ' || src.charAt(k) == '\n')) k += 1
    k
  }

  // ---- autolinks ----

  /** `<scheme:…>` or `<name@host>`, as a link; any other `<` is itself (HTML shows as the text it is). */
  private def angled(): Unit = {
    val end = src.indexOf(">", pos)
    val inside = if (end < 0) "" else src.substring(pos + 1, end)
    val colon = inside.indexOf(":")
    val uri = colon >= 2 && colon <= 32 && inside.charAt(0).isLetter && inside.indexOf(" ") < 0 &&
      inside.indexOf("<") < 0 && Inlines.scheme(inside.substring(0, colon))
    val at = inside.indexOf("@")
    val email = !uri && at > 0 && at < inside.length - 1 && inside.indexOf(" ") < 0 && inside.indexOf(":") < 0 &&
      inside.indexOf("\\") < 0 && inside.substring(at + 1).indexOf(".") > 0
    // the few tags a rich-text document writes (an underline, a picture with a size), kept as written
    val tag = !uri && !email &&
      (inside == "u" || inside == "/u" || (inside.startsWith("img ") && inside.indexOf("<") < 0))
    if (tag) {
      flush()
      add(MdInline.Tag("<" + inside + ">"))
      pos = end + 1
    }
    else if (uri || email) {
      flush()
      add(MdInline.Hyperlink(if (email) "mailto:" + inside else inside, "", List(MdInline.Text(inside))))
      pos = end + 1
    }
    else {
      say("<")
      pos += 1
    }
  }

  /** A bare `https://…`, `http://…` or `www.…` (GFM's extended autolinks), where one starts here after a space, a
    * bracket or an emphasis mark: taken as a link. */
  private def bare(): Boolean = {
    val before = if (pos == 0) ' ' else src.charAt(pos - 1)
    val starts = src.startsWith("https://", pos) || src.startsWith("http://", pos) || src.startsWith("www.", pos)
    if (!starts || !(Inlines.space(before) || before == '*' || before == '_' || before == '~' || before == '(')) false
    else {
      var k = pos
      while (k < src.length && !Inlines.space(src.charAt(k)) && src.charAt(k) != '<') k += 1
      var end = k
      // trailing punctuation is the sentence's, not the link's; a closing bracket only when it closes nothing
      var trimming = true
      while (trimming && end > pos) {
        val c = src.charAt(end - 1)
        if (c == '?' || c == '!' || c == '.' || c == ',' || c == ':' || c == '*' || c == '_' || c == '~' ||
          c == '\'' || c == '"') end -= 1
        else if (c == ')' && count(pos, end, ')') > count(pos, end, '(')) end -= 1
        else trimming = false
      }
      val text = src.substring(pos, end)
      val host = text.substring(if (text.startsWith("www.")) 0 else text.indexOf("//") + 2)
      val dot = host.indexOf(".")
      if (dot <= 0 || dot >= host.length - 1) false
      else {
        flush()
        add(MdInline.Hyperlink(if (text.startsWith("www.")) "http://" + text else text, "", List(MdInline.Text(text))))
        pos = end
        true
      }
    }
  }

  private def count(from: Int, until: Int, c: Char): Int = {
    var n = 0
    var k = from
    while (k < until) {
      if (src.charAt(k) == c) n += 1
      k += 1
    }
    n
  }
}

object Inlines {
  private val punctuationMarks: String = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

  /** The pieces of a paragraph's source `src`, resolving references by `refs` (normalised labels). */
  def parse(src: String, refs: Map[String, MdReference]): List[MdInline] =
    if (src.isEmpty) Nil else new Inlines(src, refs).parse()

  /** Whether `c` is punctuation to emphasis: ASCII's, or anything that is neither a letter, a digit nor a space. */
  def punctuation(c: Char): Boolean =
    punctuationMarks.indexOf(c.toString) >= 0 || (c > '\u007f' && !c.isLetterOrDigit && !c.isWhitespace)

  def space(c: Char): Boolean = c == ' ' || c == '\n' || c == '\t' || c == '\u00a0' || c.isWhitespace

  /** Whether `s` is a URI scheme: a letter, then letters, digits, `+`, `.` and `-`. */
  def scheme(s: String): Boolean = {
    var ok = s.nonEmpty
    var k = 0
    while (k < s.length) {
      val c = s.charAt(k)
      if (!(c.isLetterOrDigit || c == '+' || c == '.' || c == '-') || c > '\u007f') ok = false
      k += 1
    }
    ok
  }

  /** `s` with its backslash escapes undone. */
  def unescape(s: String): String =
    if (s.indexOf("\\") < 0) s
    else {
      // the stretches between escapes as they are, each escaped mark without its backslash
      var out: List[String] = Nil
      var from = 0
      var k = s.indexOf("\\")
      while (k >= 0 && k + 1 < s.length) {
        val next = s.charAt(k + 1)
        if (punctuation(next) && next <= '\u007f') {
          out = next.toString :: s.substring(from, k) :: out
          from = k + 2
        }
        k = s.indexOf("\\", k + 2)
      }
      (s.substring(from) :: out).reverse.mkString
    }

  /** A link label as it is looked up: trimmed, its runs of spaces made one, in lower case. */
  def normalise(label: String): String = {
    // the words between runs of spaces, joined by one
    var out: List[String] = Nil
    val t = label.trim
    var from = -1
    var k = 0
    while (k <= t.length) {
      val gap = k == t.length || space(t.charAt(k))
      if (gap && from >= 0) {
        out = t.substring(from, k) :: out
        from = -1
      }
      else if (!gap && from < 0) from = k
      k += 1
    }
    out.reverse.mkString(" ").toLowerCase
  }
}
