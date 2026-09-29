package everyui.markdown

import everyui.component.types.TextAlign

/** Markdown's blocks (CommonMark's, and GFM's tables): which lines make paragraphs, headings, code, quotes, lists,
  * tables and rules. The words of paragraphs, headings and cells are left `MdInline.Pending`, for `InlineParser` once
  * every link reference definition is known.
  *
  * Lines come with their tabs already made spaces (`Markdown.lines`). Written as loops over indices rather than with
  * local functions or list patterns, which the Roku's compiler does not take. */
private[markdown] object BlockParser {
  /** The HTML elements whose tags start a block of HTML that can break into a paragraph (CommonMark's sixth kind). */
  private val blockTags: Set[String] = Set("address", "article", "aside", "base", "basefont", "blockquote", "body",
    "caption", "center", "col", "colgroup", "dd", "details", "dialog", "dir", "div", "dl", "dt", "fieldset",
    "figcaption", "figure", "footer", "form", "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
    "hr", "html", "iframe", "legend", "li", "link", "main", "menu", "menuitem", "nav", "noframes", "ol", "optgroup",
    "option", "p", "param", "search", "section", "summary", "table", "tbody", "td", "tfoot", "th", "thead", "title",
    "tr", "track", "ul")
  /** The elements whose content runs until their closing tag, blank lines and all (CommonMark's first kind). */
  private val rawTags: Set[String] = Set("script", "pre", "style", "textarea")

  /** The blocks of `lines`, each with the index of its first line. */
  def parse(lines: Vector[String]): BlockRun = {
    var blocks: Vector[MdBlock] = Vector.empty
    var starts: Vector[Int] = Vector.empty
    var gapped = false
    var blankBefore = false
    var i = 0
    while (i < lines.size) {
      if (blank(lines(i))) {
        blankBefore = true
        i += 1
      }
      else {
        if (blankBefore && blocks.nonEmpty) gapped = true
        blankBefore = false
        val (block, end) = next(lines, i)
        blocks = blocks :+ block
        starts = starts :+ i
        i = end
      }
    }
    BlockRun(blocks, starts, gapped)
  }

  /** The block that starts on line `i` (not a blank one), and the line after it. */
  private def next(lines: Vector[String], i: Int): (MdBlock, Int) = {
    val line = lines(i)
    val ind = indent(line)
    if (ind >= 4) indentedCode(lines, i)
    else if (fenceOf(line).nonEmpty) fenced(lines, i)
    else if (atxLevel(line) > 0) (atx(line), i + 1)
    else if (thematic(line)) (MdBlock.Rule, i + 1)
    else if (quoted(line)) quote(lines, i)
    else if (marker(line).nonEmpty) list(lines, i)
    else if (htmlStart(line, false)) html(lines, i)
    else if (reference(line).nonEmpty) definitions(lines, i)
    else if (tableAt(lines, i)) table(lines, i)
    else paragraph(lines, i)
  }

  // ---- lines ----

  def blank(line: String): Boolean = line.trim.isEmpty

  /** How many spaces `line` starts with. */
  def indent(line: String): Int = {
    var n = 0
    while (n < line.length && line.charAt(n) == ' ') n += 1
    n
  }

  /** `line` with up to `n` of its leading spaces taken off. */
  def dedent(line: String, n: Int): String = {
    var k = 0
    while (k < n && k < line.length && line.charAt(k) == ' ') k += 1
    line.substring(k)
  }

  /** Whether `line` would end a paragraph running on to it: a fence, a heading, a rule, a quote, HTML, or a list item
    * that has words (a numbered one only from 1). */
  def interrupts(line: String): Boolean =
    if (indent(line) >= 4) false
    else if (fenceOf(line).nonEmpty || atxLevel(line) > 0 || thematic(line) || quoted(line) || htmlStart(line, true))
      true
    else marker(line).exists(m => !m.empty && (!m.ordered || m.start == 1))

  // ---- code ----

  private def indentedCode(lines: Vector[String], i: Int): (MdBlock, Int) = {
    var j = i
    var last = i
    var going = true
    while (going && j < lines.size) {
      if (blank(lines(j))) j += 1
      else if (indent(lines(j)) >= 4) {
        last = j
        j += 1
      }
      else going = false
    }
    var out: List[String] = Nil
    var k = i
    while (k <= last) {
      out = dedent(lines(k), 4) :: out
      k += 1
    }
    (MdBlock.CodeBlock("", out.reverse.mkString("\n")), last + 1)
  }

  /** The fence `line` opens: its marker repeated (three or more backticks or tildes), or "" where it opens none. */
  def fenceOf(line: String): String = {
    val ind = indent(line)
    if (ind > 3 || ind >= line.length) ""
    else {
      val c = line.charAt(ind)
      if (c != '`' && c != '~') ""
      else {
        var k = ind
        while (k < line.length && line.charAt(k) == c) k += 1
        // a backtick fence's words may hold no backtick, or it would be code in a line
        if (k - ind < 3 || (c == '`' && line.indexOf("`", k) >= 0)) "" else line.substring(ind, k)
      }
    }
  }

  private def fenced(lines: Vector[String], i: Int): (MdBlock, Int) = {
    val line = lines(i)
    val ind = indent(line)
    val fence = fenceOf(line)
    val info = Inlines.unescape(line.substring(ind + fence.length).trim)
    val space = info.indexOf(" ")
    val language = if (space < 0) info else info.substring(0, space)
    var out: List[String] = Nil
    var j = i + 1
    var closed = false
    while (!closed && j < lines.size) {
      if (closes(lines(j), fence)) closed = true
      else {
        out = dedent(lines(j), ind) :: out
        j += 1
      }
    }
    (MdBlock.CodeBlock(language, out.reverse.mkString("\n"), closed), if (closed) j + 1 else j)
  }

  /** Whether `line` closes a code block opened with `fence`: the same character, at least as many, and nothing after. */
  private def closes(line: String, fence: String): Boolean = {
    val ind = indent(line)
    if (ind > 3) false
    else {
      val c = fence.charAt(0)
      var k = ind
      while (k < line.length && line.charAt(k) == c) k += 1
      k - ind >= fence.length && line.substring(k).trim.isEmpty
    }
  }

  // ---- headings and rules ----

  /** The level of the heading `line` is (`#` to `######` and a space), or 0. */
  def atxLevel(line: String): Int = {
    val ind = indent(line)
    if (ind > 3) 0
    else {
      var k = ind
      while (k < line.length && line.charAt(k) == '#') k += 1
      val level = k - ind
      if (level < 1 || level > 6) 0
      else if (k == line.length || line.charAt(k) == ' ') level
      else 0
    }
  }

  private def atx(line: String): MdBlock = {
    val level = atxLevel(line)
    var words = line.substring(indent(line) + level).trim
    // a closing run of #s, where it stands on its own
    var k = words.length
    while (k > 0 && words.charAt(k - 1) == '#') k -= 1
    if (k == 0) words = ""
    else if (k < words.length && words.charAt(k - 1) == ' ') words = words.substring(0, k).trim
    MdBlock.Heading(level, pending(words))
  }

  /** Whether `line` is a rule: three or more of one of `*`, `-` and `_`, and spaces. */
  def thematic(line: String): Boolean = {
    val ind = indent(line)
    if (ind > 3 || ind >= line.length) false
    else {
      val c = line.charAt(ind)
      if (c != '*' && c != '-' && c != '_') false
      else {
        var count = 0
        var other = false
        var k = ind
        while (k < line.length) {
          val d = line.charAt(k)
          if (d == c) count += 1 else if (d != ' ') other = true
          k += 1
        }
        !other && count >= 3
      }
    }
  }

  /** The level of the heading `line` underlines (`===` 1, `---` 2), or 0. */
  private def setext(line: String): Int = {
    val ind = indent(line)
    val words = line.trim
    if (ind > 3 || words.isEmpty) 0
    else {
      val c = words.charAt(0)
      var k = 0
      while (k < words.length && words.charAt(k) == c) k += 1
      if (k < words.length) 0 else if (c == '=') 1 else if (c == '-') 2 else 0
    }
  }

  // ---- quotes ----

  def quoted(line: String): Boolean = {
    val ind = indent(line)
    ind <= 3 && ind < line.length && line.charAt(ind) == '>'
  }

  /** `line` without its `>` and the space after it. */
  private def unquote(line: String): String = {
    val at = indent(line) + 1
    if (at < line.length && line.charAt(at) == ' ') line.substring(at + 1) else line.substring(at)
  }

  private def quote(lines: Vector[String], i: Int): (MdBlock, Int) = {
    var inner: Vector[String] = Vector.empty
    var j = i
    var going = true
    while (going && j < lines.size) {
      val line = lines(j)
      if (quoted(line)) {
        inner = inner :+ unquote(line)
        j += 1
      }
      else if (lazyLine(line, inner)) {
        inner = inner :+ line
        j += 1
      }
      else going = false
    }
    (MdBlock.Quote(parse(inner).blocks.toList), j)
  }

  /** Whether `line`, not marked as being in the quote or the list item whose lines so far are `inner`, still is: a
    * paragraph's lazy continuation. */
  private def lazyLine(line: String, inner: Vector[String]): Boolean =
    if (blank(line) || inner.isEmpty || blank(inner.last) || interrupts(line)) false
    else {
      // not where the lines so far leave a fence open, or end in code
      var fences = 0
      inner.foreach(l => if (fenceOf(l).nonEmpty) fences += 1)
      fences % 2 == 0 && indent(inner.last) < 4 && marker(line).isEmpty && !thematic(line)
    }

  // ---- lists ----

  /** The list item marker `line` starts with. */
  def marker(line: String): Option[ListMarker] = {
    val ind = indent(line)
    if (ind > 3 || ind >= line.length) None
    else {
      val c = line.charAt(ind)
      var end = -1
      var ordered = false
      var start = 1
      if (c == '-' || c == '+' || c == '*') end = ind + 1
      else if (c >= '0' && c <= '9') {
        var k = ind
        while (k < line.length && k - ind < 10 && line.charAt(k) >= '0' && line.charAt(k) <= '9') k += 1
        if (k - ind <= 9 && k < line.length && (line.charAt(k) == '.' || line.charAt(k) == ')')) {
          end = k + 1
          ordered = true
          start = line.substring(ind, k).toInt
        }
      }
      if (end < 0 || (end < line.length && line.charAt(end) != ' ')) None
      else {
        val mark = if (ordered) line.substring(end - 1, end) else line.substring(ind, ind + 1)
        val empty = line.substring(end).trim.isEmpty
        val spaces = indent(line.substring(end))
        val width = if (empty || spaces >= 5) end + 1 else end + spaces
        Some(ListMarker(ordered, mark, start, ind, width, empty))
      }
    }
  }

  private def list(lines: Vector[String], i: Int): (MdBlock, Int) = {
    val first = marker(lines(i)).get
    var items: Vector[MdItem] = Vector.empty
    var loose = false
    var mark = first
    var j = i
    var going = true
    while (going) {
      val line = lines(j)
      var inner: Vector[String] = Vector(if (mark.empty) "" else content(line, mark))
      var k = j + 1
      var inItem = true
      while (inItem && k < lines.size) {
        val l = lines(k)
        if (blank(l)) {
          // an item may start with one blank line, no more
          if (inner.size == 1 && mark.empty) inItem = false
          else {
            inner = inner :+ ""
            k += 1
          }
        }
        else if (indent(l) >= mark.width) {
          inner = inner :+ dedent(l, mark.width)
          k += 1
        }
        else if (lazyLine(l, inner)) {
          inner = inner :+ l
          k += 1
        }
        else inItem = false
      }
      var trailing = 0
      while (inner.nonEmpty && blank(inner.last)) {
        inner = inner.dropRight(1)
        trailing += 1
      }
      val run = parse(inner)
      if (run.gapped) loose = true
      items = items :+ task(run.blocks.toList)
      val following = if (k < lines.size && !thematic(lines(k))) marker(lines(k)) else None
      if (following.exists(m => m.ordered == first.ordered && m.mark == first.mark)) {
        if (trailing > 0) loose = true
        mark = following.get
        j = k
      }
      else {
        going = false
        j = k
      }
    }
    (MdBlock.BulletList(first.ordered, first.start, !loose, items.toList), j)
  }

  /** What stands after `mark` on its line, from the column its content starts at. */
  private def content(line: String, mark: ListMarker): String = {
    val rest = line.substring(mark.indent)
    // the marker, then up to four spaces: more is code, indented one space past the marker
    val after = mark.width - mark.indent
    if (after <= rest.length) rest.substring(after) else ""
  }

  /** An item of `blocks`, a task where its paragraph starts `[ ]` or `[x]`. */
  private def task(blocks: List[MdBlock]): MdItem = {
    val words = blocks.headOption match {
      case Some(MdBlock.Paragraph(content)) => content.headOption match {
        case Some(MdInline.Pending(w)) => w
        case _ => ""
      }
      case _ => ""
    }
    val ticks = words.length >= 3 && words.charAt(0) == '[' && words.charAt(2) == ']' &&
      (words.length == 3 || words.charAt(3) == ' ') &&
      (words.charAt(1) == ' ' || words.charAt(1) == 'x' || words.charAt(1) == 'X')
    if (!ticks) MdItem(blocks)
    else MdItem(MdBlock.Paragraph(pending(words.substring(3).trim)) :: blocks.tail, Some(words.charAt(1) != ' '))
  }

  // ---- HTML ----

  /** Whether `line` starts a block of HTML: a comment, a processing instruction, a declaration, or a tag on a line of
    * its own; `interrupting` only the kinds that may break into a paragraph. */
  def htmlStart(line: String, interrupting: Boolean): Boolean = {
    val ind = indent(line)
    if (ind > 3 || ind + 1 >= line.length || line.charAt(ind) != '<') false
    else {
      val rest = line.substring(ind + 1)
      if (rest.startsWith("!--") || rest.startsWith("?") || rest.startsWith("![CDATA[")) true
      else if (rest.charAt(0) == '!') rest.length > 1 && rest.charAt(1).isLetter
      else {
        val name = tagName(rest)
        if (name.isEmpty) false
        else if (rawTags.contains(name) && !rest.startsWith("/")) true
        else if (blockTags.contains(name)) true
        else !interrupting && line.trim.endsWith(">")
      }
    }
  }

  /** The tag name `rest` (what follows a `<`) starts with, lower case, or "". */
  private def tagName(rest: String): String = {
    val from = if (rest.startsWith("/")) 1 else 0
    var k = from
    while (k < rest.length && (rest.charAt(k).isLetterOrDigit || rest.charAt(k) == '-')) k += 1
    if (k == from || !rest.charAt(from).isLetter) ""
    else if (k < rest.length && rest.charAt(k) != ' ' && rest.charAt(k) != '>' && rest.charAt(k) != '/') ""
    else rest.substring(from, k).toLowerCase
  }

  private def html(lines: Vector[String], i: Int): (MdBlock, Int) = {
    val first = lines(i).trim
    val rest = first.substring(1)
    val name = tagName(rest)
    // what ends it: a comment's close, a raw element's closing tag, or else a blank line
    val until =
      if (rest.startsWith("!--")) "-->"
      else if (rest.startsWith("?")) "?>"
      else if (rest.startsWith("![CDATA[")) "]]>"
      else if (rest.startsWith("!")) ">"
      else if (rawTags.contains(name) && !rest.startsWith("/")) "</" + name + ">"
      else ""
    var out: List[String] = Nil
    var j = i
    var going = true
    while (going && j < lines.size) {
      val line = lines(j)
      if (until.isEmpty && blank(line)) going = false
      else {
        out = line :: out
        j += 1
        if (until.nonEmpty && line.toLowerCase.indexOf(until) >= 0) going = false
      }
    }
    (MdBlock.Html(out.reverse.mkString("\n")), j)
  }

  // ---- link reference definitions ----

  /** The definition `line` is: `[label]: href`, with a title after in quotes or brackets where it has one. */
  def reference(line: String): Option[MdReference] = {
    val ind = indent(line)
    if (ind > 3 || ind >= line.length || line.charAt(ind) != '[') None
    else {
      val close = labelEnd(line, ind)
      if (close < 0 || close + 1 >= line.length || line.charAt(close + 1) != ':') None
      else {
        val label = Inlines.normalise(line.substring(ind + 1, close))
        val after = line.substring(close + 2).trim
        if (label.isEmpty || after.isEmpty) None
        else {
          var href = ""
          var rest = ""
          if (after.charAt(0) == '<') {
            val end = after.indexOf(">")
            if (end > 0) {
              href = after.substring(1, end)
              rest = after.substring(end + 1)
            }
            else rest = "\u0000"
          }
          else {
            var k = 0
            while (k < after.length && after.charAt(k) != ' ') k += 1
            href = after.substring(0, k)
            rest = after.substring(k)
          }
          val title = rest.trim
          if (rest.nonEmpty && title.nonEmpty && rest.charAt(0) != ' ') None
          else if (title.isEmpty) Some(MdReference(label, Inlines.unescape(href), ""))
          else {
            val open = title.charAt(0)
            val shut = if (open == '(') ')' else open
            if ((open == '"' || open == '\'' || open == '(') && title.length >= 2 && title.charAt(title.length - 1) == shut)
              Some(MdReference(label, Inlines.unescape(href),
                Inlines.unescape(Entities.decode(title.substring(1, title.length - 1)))))
            else None
          }
        }
      }
    }
  }

  /** The index of the `]` closing the label that opens at `open`, or -1: brackets inside it must be escaped. */
  private def labelEnd(line: String, open: Int): Int = {
    var k = open + 1
    var found = -1
    var going = true
    while (going && k < line.length) {
      val c = line.charAt(k)
      if (c == '\\') k += 2
      else if (c == '[') going = false
      else if (c == ']') {
        found = k
        going = false
      }
      else k += 1
    }
    if (found - open > 1000) -1 else found
  }

  private def definitions(lines: Vector[String], i: Int): (MdBlock, Int) = {
    var found: Vector[MdReference] = Vector.empty
    var j = i
    var going = true
    while (going && j < lines.size) {
      val r = if (blank(lines(j))) None else reference(lines(j))
      if (r.nonEmpty) {
        found = found :+ r.get
        j += 1
      }
      else going = false
    }
    (MdBlock.Definitions(found.toList), j)
  }

  // ---- tables ----

  /** Whether a table starts on line `i`: a row of cells over a row of dashes, as many of each. */
  private def tableAt(lines: Vector[String], i: Int): Boolean =
    i + 1 < lines.size && lines(i).indexOf("|") >= 0 && lines(i + 1).indexOf("|") >= 0 && indent(lines(i)) < 4 &&
      alignments(lines(i + 1)).nonEmpty && cells(lines(i)).size == alignments(lines(i + 1)).get.size

  /** How each column of the table whose delimiter row is `line` is aligned, where `line` is one. */
  private def alignments(line: String): Option[List[TextAlign]] = {
    if (indent(line) > 3) None
    else {
      val parts = cells(line)
      var out: List[TextAlign] = Nil
      var ok = parts.nonEmpty
      parts.foreach { part =>
        val p = part.trim
        val left = p.startsWith(":")
        val right = p.endsWith(":") && p.length > 1
        val dashes = p.substring(if (left) 1 else 0, if (right) p.length - 1 else p.length)
        if (dashes.isEmpty || dashes.exists(_ != '-')) ok = false
        out = (if (left && right) TextAlign.Center else if (right) TextAlign.End else TextAlign.Start) :: out
      }
      if (ok) Some(out.reverse) else None
    }
  }

  /** A table row's cells: split at the pipes not escaped, those at either end left off, each trimmed. */
  def cells(line: String): List[String] = {
    var row = line.trim
    if (row.startsWith("|")) row = row.substring(1)
    if (row.endsWith("|") && !row.endsWith("\\|")) row = row.substring(0, row.length - 1)
    var out: List[String] = Nil
    var cell = ""
    var k = 0
    while (k < row.length) {
      val c = row.charAt(k)
      if (c == '\\' && k + 1 < row.length && row.charAt(k + 1) == '|') {
        cell = cell + "|"
        k += 2
      }
      else if (c == '|') {
        out = cell.trim :: out
        cell = ""
        k += 1
      }
      else {
        cell = cell + c
        k += 1
      }
    }
    out = cell.trim :: out
    out.reverse
  }

  private def table(lines: Vector[String], i: Int): (MdBlock, Int) = {
    val align = alignments(lines(i + 1)).get
    val header = cells(lines(i))
    var rows: Vector[List[List[MdInline]]] = Vector.empty
    var j = i + 2
    var going = true
    while (going && j < lines.size) {
      val line = lines(j)
      if (blank(line) || interrupts(line)) going = false
      else {
        val row = cells(line)
        // as many cells as the header has: missing ones empty, extra ones left off
        rows = rows :+ align.indices.toList.map(c => pending(if (c < row.size) row(c) else ""))
        j += 1
      }
    }
    (MdBlock.TableBlock(align, header.map(pending), rows.toList), j)
  }

  // ---- paragraphs ----

  private def paragraph(lines: Vector[String], i: Int): (MdBlock, Int) = {
    var j = i + 1
    var level = 0
    var going = true
    while (going && j < lines.size) {
      val line = lines(j)
      if (blank(line)) going = false
      else if (indent(line) < 4 && setext(line) > 0) {
        level = setext(line)
        going = false
      }
      else if (interrupts(line) || tableAt(lines, j)) going = false
      else j += 1
    }
    var out: List[String] = Nil
    var k = i
    while (k < j) {
      out = dedent(lines(k), lines(k).length) :: out
      k += 1
    }
    val words = trimEnd(out.reverse.mkString("\n"))
    if (level > 0) (MdBlock.Heading(level, pending(words)), j + 1)
    else (MdBlock.Paragraph(pending(words)), j)
  }

  private def trimEnd(s: String): String = {
    var k = s.length
    while (k > 0 && (s.charAt(k - 1) == ' ' || s.charAt(k - 1) == '\t')) k -= 1
    s.substring(0, k)
  }

  private def pending(words: String): List[MdInline] = List(MdInline.Pending(words))
}
