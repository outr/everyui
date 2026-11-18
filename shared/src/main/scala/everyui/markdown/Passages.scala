package everyui.markdown

import everyui.text.TextRun

/** Where passages (`Passage`) lie in a Markdown document's words, and their runs marked.
  *
  * A passage is named by its words as written in the Markdown, and found in the words as drawn: its first occurrence,
  * reading the document's units (each line of a paragraph or a heading, each table cell, in order; never code) as one
  * text with a line break between each. Any run of spaces and line breaks in the passage matches any in the words, or
  * none, so a passage may run across lines, list items and table cells. Markdown's marks in the passage (`**`, `*`,
  * `_`, `~~`, `` ` ``, `|`, a link's brackets and its `(address)`, a backslash's escape) are passed over where the words
  * do not hold them as they are, as are a list's, a quote's and a heading's marks at the start of one of its lines, so
  * "**Shall I** start" finds "Shall I start". A passage whose words are not there is found nowhere. */
private[everyui] object Passages {
  // the marks passed over where the words do not hold them
  private val marks = "*_~`[]|"

  /** For each unit of `units`, where `passages` lie in it, by the order they start in. */
  def find(units: Vector[String], passages: Vector[Passage]): Vector[Vector[MarkSpan]] = {
    val joined = units.mkString("\n")
    var starts = Vector.empty[Int]
    var next = 0
    units.foreach { u =>
      starts = starts :+ next
      next = next + u.length + 1
    }
    var out = Vector.fill(units.size)(Vector.empty[MarkSpan])
    passages.indices.foreach { p =>
      at(joined, passages(p).text).foreach { case (from, to) =>
        units.indices.foreach { u =>
          val s = math.max(from, starts(u))
          val e = math.min(to, starts(u) + units(u).length)
          if (e > s) out = out.updated(u, out(u) :+ MarkSpan(s - starts(u), e - starts(u), p, passages(p).tone))
        }
      }
    }
    out.map(_.sortBy(_.from))
  }

  /** Where `passage` first lies in `text`, from its first character up to just past its last: none where it is not
    * there, or holds no words. */
  def at(text: String, passage: String): Option[(Int, Int)] = {
    val p = passage.trim
    var start = 0
    var found: Option[(Int, Int)] = None
    if (p.nonEmpty) while (found.isEmpty && start < text.length) {
      if (!text.charAt(start).isWhitespace) {
        val end = from(text, start, p)
        if (end > start) found = Some((start, end))
      }
      start += 1
    }
    found
  }

  // just past the last character of `p` matched in `t` from `s`, or -1
  private def from(t: String, s: Int, p: String): Int = {
    var i = 0
    var j = s
    var end = -1
    var ok = true
    var lineStart = true
    while (ok && i < p.length) {
      val c = p.charAt(i)
      val literal = j < t.length && c == t.charAt(j)
      if (c.isWhitespace) {
        while (i < p.length && p.charAt(i).isWhitespace) {
          if (p.charAt(i) == '\n') lineStart = true
          i += 1
        }
        while (j < t.length && t.charAt(j).isWhitespace) j += 1
      }
      else if (lineStart && !literal && blockMark(p, i) > 0) {
        i += blockMark(p, i)
        lineStart = false
      }
      else if (literal) {
        i += 1
        j += 1
        end = j
        lineStart = false
      }
      else if (c == '\\' && i + 1 < p.length && !p.charAt(i + 1).isLetterOrDigit && !p.charAt(i + 1).isWhitespace) i += 1
      else if (c == ']' && i + 1 < p.length && (p.charAt(i + 1) == '(' || p.charAt(i + 1) == '[')) {
        val close = p.indexOf(if (p.charAt(i + 1) == '(') ')' else ']', i + 2)
        i = if (close < 0) p.length else close + 1
      }
      else if (marks.indexOf(c.toInt) >= 0) i += 1
      else ok = false
    }
    if (ok && end > s) end else -1
  }

  // how long the list's, quote's or heading's mark at `i` of `p` is, with the spaces after it: 0 where there is none
  private def blockMark(p: String, i: Int): Int = {
    var k = i
    val c = p.charAt(i)
    if (c == '-' || c == '*' || c == '+' || c == '>') k += 1
    else if (c == '#') while (k < p.length && p.charAt(k) == '#') k += 1
    else {
      while (k < p.length && p.charAt(k).isDigit) k += 1
      if (k > i && k < p.length && (p.charAt(k) == '.' || p.charAt(k) == ')')) k += 1 else k = i
    }
    if (k == i || (k < p.length && !p.charAt(k).isWhitespace)) 0
    else {
      while (k < p.length && (p.charAt(k) == ' ' || p.charAt(k) == '\t')) k += 1
      k - i
    }
  }

  /** The units of `blocks`' words, as a `MarkdownView` draws them (`Markable.markWords`): each line of a paragraph or
    * a heading, then each table cell, row by row, through lists and quotes; no code. */
  def units(blocks: Seq[MdBlock]): Vector[String] = blocks.toVector.flatMap {
    case MdBlock.Paragraph(content) => lines(content)
    case MdBlock.Heading(_, content) => lines(content)
    case MdBlock.Quote(inside) => units(inside)
    case MdBlock.BulletList(_, _, _, items) => items.toVector.flatMap(i => units(i.blocks))
    case MdBlock.TableBlock(_, header, rows) => (header :: rows).toVector.flatMap(_.map(cell))
    case _ => Vector.empty
  }

  /** Which of `passages` are found in Markdown `text`, as a `MarkdownView` of it marks them. */
  def found(text: String, passages: Vector[Passage]): Vector[Boolean] = {
    val spans = find(units(Markdown.parse(text)), passages).flatten
    passages.indices.toVector.map(p => spans.exists(_.passage == p))
  }

  // a paragraph's lines' words, as drawn with each line break of the text a line (a chat's)
  private def lines(content: List[MdInline]): Vector[String] =
    MarkdownRuns.parts(content, 14.0, 400, breaks = true).toVector.map(words)
  /** A table cell's words, as drawn. */
  def cell(content: List[MdInline]): String = MarkdownRuns.parts(content, 14.0, 400).flatMap(_.runs).map(_.text).mkString
  /** A line's words, as drawn. */
  def words(part: MdPart): String = part.runs.map(_.text).mkString

  /** `runs` with `spans` of their words marked (`TextRun.mark`): cut where a span starts or ends, each piece in one
    * marked on its tone's wash with a line of it under (`Passage.wash`, `Passage.line`), a code span's or a mention's
    * own wash given up for it. A picture is marked whole where a span reaches it. */
  def marked(runs: List[TextRun], spans: Vector[MarkSpan]): List[TextRun] = if (spans.isEmpty) runs else {
    var out: List[TextRun] = Nil
    var at = 0
    runs.foreach { r =>
      val end = at + r.text.length
      if (r.image.nonEmpty || r.text.isEmpty) {
        val over = spans.find(s => s.from < end && s.to > at)
        out = over.map(s => lit(r, s)).getOrElse(r) :: out
      }
      else {
        var pos = at
        while (pos < end) {
          val over = spans.find(s => s.from <= pos && pos < s.to)
          val next = over match {
            case Some(s) => math.min(end, s.to)
            case None => spans.filter(_.from > pos).map(_.from).minOption.map(math.min(end, _)).getOrElse(end)
          }
          val piece = if (pos == at && next == end) r else r.copy(text = r.text.substring(pos - at, next - at))
          out = over.map(s => lit(piece, s)).getOrElse(piece) :: out
          pos = next
        }
      }
      at = end
    }
    out.reverse
  }

  // `r` marked in `s`'s tone
  private def lit(r: TextRun, s: MarkSpan): TextRun = r.copy(background = "",
    mark = MarkdownRuns.hex(Passage.wash(s.tone)), markLine = MarkdownRuns.hex(Passage.line(s.tone)))
}
