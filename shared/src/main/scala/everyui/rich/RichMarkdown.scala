package everyui.rich

import everyui.markdown.{Markdown, MdBlock, MdInline, MdItem}
import everyui.rich.RichInline.{Image, Text}

/** A rich document as Markdown and back (`RichDoc.markdown`, `RichDoc.fromMarkdown`): CommonMark with GitHub's
  * strikethrough, an underline as `<u>…</u>`, and a picture with a size as `<img>`, which is everything the model holds
  * written so it reads back the same. Words are escaped wherever they would read as Markdown; the spaces at a styled
  * stretch's edges are written outside its marks, where CommonMark would not take them inside.
  *
  * Read from anything else, what the model has no place for is kept as words: a table's rows, raw HTML, a heading of
  * level 4 to 6 as one of 3. */
object RichMarkdown {
  def write(doc: RichDoc): String = written(doc)._1

  /** Where `at` falls in `write(doc)`. */
  def offset(doc: RichDoc, at: RichPos): Int = {
    val places = written(doc)._2
    val p = places(at.block)
    p(math.min(at.offset, p.length - 1))
  }

  // ---- writing ----

  // the Markdown, and for each block where each of its offsets falls in it (one more than the block is long)
  private def written(doc: RichDoc): (String, Vector[Array[Int]]) = {
    val out = new StringBuilder
    var places = Vector.empty[Array[Int]]
    // each list depth's indent for what is nested in it, from the item last written at that depth
    var indents = Vector.empty[Int]
    // the numbers of the numbered lists open at each depth
    var numbers = Vector.empty[Int]
    var previous: Option[BlockKind] = None
    doc.blocks.foreach { b =>
      val listDepth = BlockKind.depth(b.kind)
      val gap = (previous, b.kind) match {
        case (None, _) => ""
        case (Some(p), _) if BlockKind.depth(p).nonEmpty && listDepth.nonEmpty => "\n"
        case (Some(BlockKind.Quote), BlockKind.Quote) => "\n>\n"
        case _ => "\n\n"
      }
      out.append(gap)
      if (listDepth.isEmpty) {
        indents = Vector.empty
        numbers = Vector.empty
      }
      val place = new Array[Int](b.length + 1)
      b.kind match {
        case BlockKind.Paragraph => runs(out, b.inlines, place, "", start = true)
        case BlockKind.Heading(level) =>
          out.append("#" * math.max(1, math.min(3, level)) + " ")
          runs(out, b.inlines, place, "", start = false)
        case BlockKind.Quote =>
          out.append("> ")
          runs(out, b.inlines, place, "> ", start = true)
        case BlockKind.Code(language) =>
          val code = b.text
          val fence = "`" * math.max(3, longest(code, '`') + 1)
          out.append(fence + language + "\n")
          var k = 0
          while (k <= code.length) {
            place(k) = out.length
            if (k < code.length) out.append(code.charAt(k))
            k += 1
          }
          out.append("\n" + fence)
        case BlockKind.Rule =>
          place(0) = out.length
          out.append("---")
        case BlockKind.Picture(image) =>
          place(0) = out.length
          out.append(picture(image))
        case kind =>
          val depth = listDepth.getOrElse(0)
          val indent = (0 until depth).map(d => indents.lift(d).getOrElse(2)).sum
          val ordered = kind.isInstanceOf[BlockKind.Numbered]
          numbers = numbers.take(depth + 1).padTo(depth + 1, 0)
          val number = if (ordered && previous.exists(p => sameList(p, kind, depth))) numbers(depth) + 1 else 1
          numbers = numbers.updated(depth, if (ordered) number else 0)
          val marker = if (ordered) number.toString + ". " else "- "
          indents = indents.take(depth).padTo(depth, 2) :+ marker.length
          out.append(" " * indent + marker)
          runs(out, b.inlines, place, " " * (indent + marker.length), start = true)
      }
      places = places :+ place
      previous = Some(b.kind)
    }
    (out.toString, places)
  }

  // whether a numbered item at `depth` goes on the list `previous` was in (one at the same depth, or nested deeper)
  private def sameList(previous: BlockKind, kind: BlockKind, depth: Int): Boolean = previous match {
    case BlockKind.Numbered(d) => d >= depth
    case BlockKind.Bullet(d) => d > depth
    case _ => false
  }

  private def longest(s: String, c: Char): Int = {
    var best = 0
    var run = 0
    s.foreach { ch =>
      run = if (ch == c) run + 1 else 0
      best = math.max(best, run)
    }
    best
  }

  // the marks written as delimiters, outermost first; underline as a tag, code as a span of its own
  private val delimited: List[(Mark, String)] = List(Mark.Bold -> "**", Mark.Italic -> "*", Mark.Strike -> "~~")

  private def runs(out: StringBuilder, inlines: Vector[RichInline], place: Array[Int], continuation: String,
                   start: Boolean): Unit = {
    val pieces = spaced(inlines)
    var open: List[Mark] = Nil
    var at = 0
    var lineStart = start
    def opening(m: Mark): String = m match {
      case Mark.Link(_) => "["
      case Mark.Underline => "<u>"
      case _ => delimited.find(_._1 == m).map(_._2).getOrElse("")
    }
    def closing(m: Mark): String = m match {
      case Mark.Link(href) => "](" + destination(href) + ")"
      case Mark.Underline => "</u>"
      case _ => delimited.find(_._1 == m).map(_._2).getOrElse("")
    }
    // the order marks open in: a link outermost, then the delimiters, then the underline
    def order(m: Mark): Int = m match {
      case Mark.Link(_) => 0
      case Mark.Bold => 1
      case Mark.Italic => 2
      case Mark.Strike => 3
      case Mark.Underline => 4
      case Mark.Code => 5
    }
    pieces.foreach { piece =>
      val wanted = piece.marks.filterNot(_ == Mark.Code).toList.sortBy(order)
      // close down to the longest run of open marks this piece keeps, then open the rest
      val keep = open.zip(wanted).takeWhile { case (a, b) => a == b }.length
      open.drop(keep).reverse.foreach(m => out.append(closing(m)))
      open = open.take(keep)
      wanted.drop(keep).foreach { m =>
        out.append(opening(m))
        lineStart = false
      }
      open = open ++ wanted.drop(keep)
      piece match {
        case Text(t, marks) if marks.contains(Mark.Code) =>
          val ticks = "`" * (longest(t, '`') + 1)
          // a space each side where a backtick or a space at both ends would be taken for the span's own
          val padded = t.startsWith("`") || t.endsWith("`") || (t.startsWith(" ") && t.endsWith(" ") && t.trim.nonEmpty)
          val pad = if (padded) " " else ""
          out.append(ticks + pad)
          t.indices.foreach { k =>
            place(at + k) = out.length
            out.append(if (t.charAt(k) == '\n') ' ' else t.charAt(k))
          }
          out.append(pad + ticks)
          at += t.length
          lineStart = false
        case Text(t, _) =>
          t.indices.foreach { k =>
            place(at + k) = out.length
            val c = t.charAt(k)
            if (c == '\n') {
              out.append("\\\n" + continuation)
              lineStart = true
            }
            else {
              out.append(escaped(t, k, lineStart))
              lineStart = false
            }
          }
          at += t.length
        case Image(image, _) =>
          place(at) = out.length
          out.append(picture(image))
          at += 1
          lineStart = false
      }
    }
    open.reverse.foreach(m => out.append(closing(m)))
    place(at) = out.length
  }

  // the runs with the spaces at the edges of each styled stretch moved outside its delimited marks
  private def spaced(inlines: Vector[RichInline]): Vector[RichInline] = {
    val split = inlines.flatMap {
      case Text(t, m) if m.contains(Mark.Code) => Vector(Text(t, m))
      case Text(t, m) =>
        val lead = t.takeWhile(_ == ' ')
        val rest = t.drop(lead.length)
        val trail = rest.reverse.takeWhile(_ == ' ')
        val core = rest.dropRight(trail.length)
        Vector(Text(lead, m), Text(core, m), Text(trail, m)).filter(_.text.nonEmpty)
      case image => Vector(image)
    }
    val delimiters: Set[Mark] = delimited.map(_._1).toSet
    val n = split.length
    RichBlock.normal(split.indices.toVector.map { i =>
      split(i) match {
        case Text(t, m) if t.nonEmpty && t.forall(_ == ' ') =>
          def near(step: Int): Set[Mark] = {
            var j = i + step
            while (j >= 0 && j < n && (split(j) match {
              case Text(u, _) => u.forall(_ == ' ')
              case _ => false
            })) j += step
            if (j >= 0 && j < n) split(j).marks else Set.empty
          }
          val kept = near(-1) intersect near(1)
          Text(t, m.filter(x => !delimiters.contains(x) || kept.contains(x)))
        case other => other
      }
    })
  }

  private def picture(image: RichImage): String =
    if (image.width.isEmpty && image.height.isEmpty)
      "![" + escapedAll(image.alt) + "](" + destination(image.source) + ")"
    else "<img src=\"" + attribute(image.source) + "\" alt=\"" + attribute(image.alt) + "\"" +
      image.width.map(w => " width=\"" + number(w) + "\"").getOrElse("") +
      image.height.map(h => " height=\"" + number(h) + "\"").getOrElse("") + ">"

  private def number(d: Double): String = if (d == math.rint(d)) d.toLong.toString else d.toString

  private def attribute(s: String): String =
    s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

  // a link's or a picture's address: in angle brackets where it holds a space or a bracket
  private def destination(href: String): String =
    if (href.exists(c => c == ' ' || c == '(' || c == ')' || c == '<' || c == '>'))
      "<" + href.replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>") + ">"
    else href.replace("\\", "\\\\")

  // what reads as Markdown wherever it stands
  private val special = "\\`*_~[]<"

  // the character at `k` of `t`, escaped where it would read as Markdown
  private def escaped(t: String, k: Int, lineStart: Boolean): String = {
    val c = t.charAt(k)
    val next = if (k + 1 < t.length) t.charAt(k + 1) else ' '
    if (special.indexOf(c) >= 0) "\\" + c
    // a picture's start, a character reference, an autolink's end
    else if (c == '!' && next == '[') "\\!"
    else if (c == '&' && (next.isLetter || next == '#')) "\\&"
    else if (lineStart && (c == '#' || c == '-' || c == '+' || c == '=' || c == '|' || c == '>')) "\\" + c
    else if (lineStart && c == ' ') "&#32;"
    else if (lineStart && c.isDigit) {
      // "1." or "1)" at a line's start would be a list
      var j = k
      while (j < t.length && t.charAt(j).isDigit) j += 1
      if (j < t.length && (t.charAt(j) == '.' || t.charAt(j) == ')')) c.toString + t.substring(k + 1, j) + "\\"
      else c.toString
    }
    else c.toString
  }

  private def escapedAll(s: String): String = s.indices.map(k => escaped(s, k, lineStart = false)).mkString

  // ---- reading ----

  def read(source: String): RichDoc = RichDoc.of(blocks(Markdown.parse(source), 0, None))

  private def blocks(md: List[MdBlock], depth: Int, quoted: Option[BlockKind]): Vector[RichBlock] =
    md.toVector.flatMap {
    case MdBlock.Paragraph(content) =>
      val pieces = inlines(content)
      pieces match {
        case Vector(Image(image, m)) if m.isEmpty && quoted.isEmpty => Vector(RichBlock(BlockKind.Picture(image)))
        case _ => Vector(RichBlock(quoted.getOrElse(BlockKind.Paragraph), pieces))
      }
    case MdBlock.Heading(level, content) =>
      Vector(RichBlock(quoted.getOrElse(BlockKind.Heading(math.max(1, math.min(3, level)))), inlines(content)))
    case MdBlock.CodeBlock(language, code, _) =>
      val words = if (code.isEmpty) Vector.empty else Vector(Text(code.stripSuffix("\n")))
      Vector(RichBlock(BlockKind.Code(language), words))
    case MdBlock.Quote(inside) => blocks(inside, depth, Some(BlockKind.Quote))
    case MdBlock.BulletList(ordered, _, _, items) => items.toVector.flatMap(item(_, ordered, depth))
    case MdBlock.Rule => Vector(RichBlock(BlockKind.Rule))
    case MdBlock.TableBlock(_, header, rows) =>
      (header :: rows).toVector.map(cells => RichBlock(quoted.getOrElse(BlockKind.Paragraph),
        RichBlock.normal(cells.toVector.zipWithIndex.flatMap { case (c, i) =>
          (if (i > 0) Vector(Text(" | ")) else Vector.empty) ++ inlines(c)
        })))
    // a picture with a size, alone on its line, is a block of HTML to Markdown
    case MdBlock.Html(source) if quoted.isEmpty && lone(source.trim) =>
      imageTag(source.trim).map(i => RichBlock(BlockKind.Picture(i))).toVector
    case MdBlock.Html(source) =>
      Vector(RichBlock(quoted.getOrElse(BlockKind.Paragraph), Vector(Text(source.stripSuffix("\n")))))
    case MdBlock.Definitions(_) => Vector.empty
  }

  private def item(item: MdItem, ordered: Boolean, depth: Int): Vector[RichBlock] = {
    val kind: BlockKind = if (ordered) BlockKind.Numbered(math.min(depth, RichDoc.deepest))
    else BlockKind.Bullet(math.min(depth, RichDoc.deepest))
    val (first, rest) = item.blocks match {
      case MdBlock.Paragraph(content) :: more => (inlines(content), more)
      case more => (Vector.empty[RichInline], more)
    }
    RichBlock(kind, first) +: rest.toVector.flatMap {
      case list: MdBlock.BulletList => blocks(List(list), depth + 1, None)
      case MdBlock.Paragraph(content) => Vector(RichBlock(kind, inlines(content)))
      case other => blocks(List(other), depth, None)
    }
  }

  private def inlines(md: List[MdInline]): Vector[RichInline] = {
    var underline = 0
    def walk(md: List[MdInline], marks: Set[Mark]): Vector[RichInline] = md.toVector.flatMap {
      case MdInline.Text(t) => Vector(Text(t, under(marks)))
      case MdInline.Emphasis(c) => walk(c, marks + Mark.Italic)
      case MdInline.Strong(c) => walk(c, marks + Mark.Bold)
      case MdInline.Strike(c) => walk(c, marks + Mark.Strike)
      case MdInline.CodeSpan(code) => Vector(Text(code, under(marks) + Mark.Code))
      case MdInline.Hyperlink(href, _, c) => walk(c, marks.filterNot(_.isInstanceOf[Mark.Link]) + Mark.Link(href))
      case MdInline.Picture(src, _, alt) => Vector(Image(RichImage(src, alt), under(marks)))
      case MdInline.SoftBreak => Vector(Text(" ", under(marks)))
      case MdInline.HardBreak => Vector(Text("\n", under(marks)))
      case MdInline.Tag("<u>") =>
        underline += 1
        Vector.empty
      case MdInline.Tag("</u>") =>
        underline = math.max(0, underline - 1)
        Vector.empty
      case MdInline.Tag(source) => imageTag(source).map(i => Vector(Image(i, under(marks))))
        .getOrElse(Vector(Text(source, under(marks))))
      case MdInline.Pending(source) => Vector(Text(source, under(marks)))
    }
    def under(marks: Set[Mark]): Set[Mark] = if (underline > 0) marks + Mark.Underline else marks
    RichBlock.normal(walk(md, Set.empty))
  }

  private def imageTag(source: String): Option[RichImage] = {
    val attr = """([a-zA-Z]+)\s*=\s*"([^"]*)"""".r
    val found = attr.findAllMatchIn(source).map(m => m.group(1).toLowerCase -> unattribute(m.group(2))).toMap
    found.get("src").map(src => RichImage(src, found.getOrElse("alt", ""),
      found.get("width").flatMap(_.toDoubleOption), found.get("height").flatMap(_.toDoubleOption)))
  }

  private def lone(s: String): Boolean =
    s.startsWith("<img ") && s.endsWith(">") && s.indexOf(">") == s.length - 1 && imageTag(s).nonEmpty

  private def unattribute(s: String): String =
    s.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
