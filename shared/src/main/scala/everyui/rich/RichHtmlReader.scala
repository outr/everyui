package everyui.rich

import everyui.markdown.Entities
import everyui.rich.RichInline.{Image, Text}

/** HTML read into a rich document (`RichDoc.fromHtml`), without a DOM: what a paste brings from a page, a mail or a
  * document editor. Paragraphs, divisions and table rows are paragraphs; `h1` to `h6` headings (4 to 6 as 3); `li`
  * list items nested as deep as their lists, numbered under `ol`; `blockquote` quotes; `pre` code; `hr` a rule; an
  * `img` alone in its block a picture of its own, else one among the words; `br` a line break. Marks from `b`,
  * `strong`, `i`, `em`, `u`, `ins`, `s`, `strike`, `del`, `code`, `a href`, and from inline styles (`font-weight: 700`,
  * `font-style: italic`, `text-decoration`), which document editors paste instead (a bold `b` they mark normal is not
  * bold). Script, style and the head are left out; anything else is its words. */
private[rich] object RichHtmlReader {
  private val unseen = Set("script", "style", "head", "title", "template", "noscript", "iframe", "object", "svg")
  private val paragraphs = Set("p", "div", "section", "article", "header", "footer", "main", "nav", "aside", "tr",
    "address", "figure", "figcaption", "dt", "dd", "center", "form", "fieldset", "details", "summary")

  def read(html: String): RichDoc = {
    val reader = new Reader(html)
    reader.run()
    RichDoc.of(reader.blocks.filter(b => !BlockKind.holdsWords(b.kind) || b.length > 0 || isList(b.kind)))
  }

  private def isList(kind: BlockKind): Boolean = BlockKind.depth(kind).nonEmpty

  // a tag open: its name, and what it put on: marks, a link, a kind of block
  private case class Open(name: String, marks: Set[Mark], removes: Set[Mark])

  private class Reader(html: String) {
    private val lower = html.toLowerCase
    var blocks: Vector[RichBlock] = Vector.empty
    private var kind: BlockKind = BlockKind.Paragraph
    private var pieces: Vector[RichInline] = Vector.empty
    private var stack: List[Open] = Nil
    // the lists open, innermost first: numbered or not
    private var lists: List[Boolean] = Nil
    private var quotes = 0
    private var pre = 0
    private var space = false
    // an item opened with nothing of it written yet: an empty one (`<li></li>`) is kept, a list's closing tag alone not
    private var itemOpen = false

    private def marks: Set[Mark] = stack.reverse.foldLeft(Set.empty[Mark])((m, o) => (m -- o.removes) ++ o.marks)

    def run(): Unit = {
      var i = 0
      var from = 0
      while (i < html.length) {
        if (html.charAt(i) == '<' && i + 1 < html.length && opens(html.charAt(i + 1))) {
          words(html.substring(from, i))
          i = tag(i)
          from = i
        }
        else i += 1
      }
      words(html.substring(math.min(from, html.length)))
      end()
    }

    private def opens(c: Char): Boolean =
      c == '/' || c == '!' || c == '?' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

    // the block being read ended, kept where it holds anything
    private def end(item: Boolean = false): Unit = {
      val trimmed = RichBlock.normal(trimEnd(pieces))
      if (trimmed.nonEmpty || (item && itemOpen && isList(kind))) {
        blocks = blocks :+ RichBlock(kind, trimmed)
        itemOpen = false
      }
      pieces = Vector.empty
      space = false
      kind = if (lists.nonEmpty) listKind else if (quotes > 0) BlockKind.Quote else BlockKind.Paragraph
    }

    private def listKind: BlockKind =
      if (lists.headOption.contains(true)) BlockKind.Numbered(math.min(RichDoc.deepest, lists.length - 1))
      else BlockKind.Bullet(math.min(RichDoc.deepest, lists.length - 1))

    private def trimEnd(v: Vector[RichInline]): Vector[RichInline] = v.lastOption match {
      case Some(Text(t, m)) if pre == 0 && t.endsWith(" ") => v.init :+ Text(t.reverse.dropWhile(_ == ' ').reverse, m)
      case _ => v
    }

    private def tag(at: Int): Int =
      if (html.startsWith("<!--", at)) {
        val e = html.indexOf("-->", at + 4)
        if (e < 0) html.length else e + 3
      }
      else {
        val end = close(at)
        val body = html.substring(at + 1, math.max(at + 1, end - 1))
        val closing = body.startsWith("/")
        val name = body.stripPrefix("/").takeWhile(ch => ch.isLetterOrDigit || ch == '-').toLowerCase
        if (name.isEmpty || body.startsWith("!") || body.startsWith("?")) end
        else if (!closing && unseen.contains(name) && !body.endsWith("/")) skip(name, end)
        else {
          // a space owed before the tag goes with the marks outside it, not inside a bold or a link it opens
          if (space && pieces.nonEmpty && pre == 0 && name != "br") {
            add(Text(" ", marks))
            space = false
          }
          if (closing) closed(name) else opened(name, body)
          end
        }
      }

    private def opened(name: String, body: String): Unit = name match {
      case "br" =>
        add(Text("\n", marks))
        space = false
      case "hr" =>
        end()
        blocks = blocks :+ RichBlock(BlockKind.Rule)
      case "img" =>
        attribute(body, "src").filter(_.nonEmpty).foreach { src =>
          val image = RichImage(src, attribute(body, "alt").getOrElse(""), number(attribute(body, "width")),
            number(attribute(body, "height")))
          add(Image(image, marks))
        }
      case "ul" | "ol" =>
        end()
        lists = (name == "ol") :: lists
        kind = listKind
      case "li" =>
        end()
        kind = if (lists.isEmpty) BlockKind.Bullet() else listKind
        itemOpen = true
      case "blockquote" =>
        end()
        quotes += 1
        kind = BlockKind.Quote
      case "pre" =>
        end()
        pre += 1
        kind = BlockKind.Code()
      case h if h.length == 2 && h.charAt(0) == 'h' && h.charAt(1) >= '1' && h.charAt(1) <= '6' =>
        end()
        kind = BlockKind.Heading(math.min(3, h.charAt(1) - '0'))
      case p if paragraphs.contains(p) || p == "td" || p == "th" =>
        if (p == "td" || p == "th") { if (pieces.nonEmpty) add(Text(" ", marks)) }
        else end()
        stack = Open(name, styled(body), unstyled(body)) :: stack
      case _ =>
        val own: Set[Mark] = name match {
          case "b" | "strong" => Set(Mark.Bold)
          case "i" | "em" | "cite" | "var" => Set(Mark.Italic)
          case "u" | "ins" => Set(Mark.Underline)
          case "s" | "strike" | "del" => Set(Mark.Strike)
          case "code" | "kbd" | "samp" | "tt" => if (pre > 0) Set.empty else Set(Mark.Code)
          case "a" => attribute(body, "href").filter(_.nonEmpty).map(h => Set[Mark](Mark.Link(h))).getOrElse(Set.empty)
          case _ => Set.empty
        }
        // a document editor's `<b style="font-weight:normal">` round everything is not bold
        val removed = unstyled(body)
        stack = Open(name, (own -- removed) ++ styled(body), removed) :: stack
    }

    private def closed(name: String): Unit = name match {
      case "ul" | "ol" =>
        end()
        lists = if (lists.isEmpty) Nil else lists.tail
        kind = if (lists.nonEmpty) listKind else if (quotes > 0) BlockKind.Quote else BlockKind.Paragraph
      case "li" => end(item = true)
      case "blockquote" =>
        end()
        quotes = math.max(0, quotes - 1)
        kind = if (quotes > 0) BlockKind.Quote else BlockKind.Paragraph
      case "pre" =>
        end()
        pre = math.max(0, pre - 1)
      case h if h.length == 2 && h.charAt(0) == 'h' && h.charAt(1) >= '1' && h.charAt(1) <= '6' => end()
      case p if paragraphs.contains(p) =>
        end()
        pop(name)
      case _ => pop(name)
    }

    // the innermost open `name` and all inside it closed
    private def pop(name: String): Unit = {
      val at = stack.indexWhere(_.name == name)
      if (at >= 0) stack = stack.drop(at + 1)
    }

    private def styled(body: String): Set[Mark] = style(body).flatMap {
      case ("font-weight", v) if v == "bold" || v == "bolder" || v.toIntOption.exists(_ >= 600) => Some(Mark.Bold)
      case ("font-style", v) if v.startsWith("italic") || v.startsWith("oblique") => Some(Mark.Italic)
      case ("text-decoration" | "text-decoration-line", v) if v.contains("underline") => Some(Mark.Underline)
      case ("text-decoration" | "text-decoration-line", v) if v.contains("line-through") => Some(Mark.Strike)
      case _ => None
    }.toSet

    private def unstyled(body: String): Set[Mark] = style(body).flatMap {
      case ("font-weight", v) if v == "normal" || v.toIntOption.exists(_ < 600) => Some(Mark.Bold)
      case ("font-style", "normal") => Some(Mark.Italic)
      case _ => None
    }.toSet

    private def style(body: String): List[(String, String)] =
      attribute(body, "style").toList.flatMap(_.split(";").toList).flatMap { d =>
        val c = d.indexOf(':')
        if (c < 0) None else Some(d.substring(0, c).trim.toLowerCase -> d.substring(c + 1).trim.toLowerCase)
      }

    private def number(v: Option[String]): Option[Double] = v.flatMap(_.stripSuffix("px").trim.toDoubleOption)

    private def close(at: Int): Int = {
      var i = at + 1
      var quote: Char = 0
      var done = false
      while (!done && i < html.length) {
        val c = html.charAt(i)
        if (quote != 0) { if (c == quote) quote = 0 }
        else if (c == '"' || c == '\'') quote = c
        else if (c == '>') done = true
        i += 1
      }
      i
    }

    private def skip(name: String, from: Int): Int = {
      val e = lower.indexOf("</" + name, from)
      if (e < 0) html.length else close(e)
    }

    private def attribute(body: String, name: String): Option[String] = {
      val low = body.toLowerCase
      var at = low.indexOf(name)
      var value: Option[String] = None
      while (value.isEmpty && at > 0) {
        val before = low.charAt(at - 1)
        var i = at + name.length
        while (i < body.length && body.charAt(i) == ' ') i += 1
        if ((before == ' ' || before == '\t' || before == '\n') && i < body.length && body.charAt(i) == '=') {
          i += 1
          while (i < body.length && body.charAt(i) == ' ') i += 1
          val q = if (i < body.length) body.charAt(i) else ' '
          val raw =
            if (q == '"' || q == '\'') {
              val e = body.indexOf(q, i + 1)
              body.substring(i + 1, if (e < 0) body.length else e)
            }
            else body.substring(i).takeWhile(ch => ch != ' ' && ch != '\t' && ch != '\n')
          value = Some(Entities.decode(raw).trim)
        }
        at = low.indexOf(name, at + 1)
      }
      value
    }

    private def add(piece: RichInline): Unit = pieces = pieces :+ piece

    private def words(raw: String): Unit = if (raw.nonEmpty) {
      val text = Entities.decode(raw).replace(' ', ' ')
      if (pre > 0) add(Text(text, marks))
      else {
        val out = new StringBuilder
        text.foreach { c =>
          if (c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\f') space = true
          else {
            val atStart = pieces.isEmpty && out.isEmpty
            val afterBreak = out.isEmpty && pieces.lastOption.exists {
              case Text(t, _) => t.endsWith("\n") || t.endsWith(" ")
              case _ => false
            }
            if (space && !atStart && !afterBreak) out.append(' ')
            space = false
            out.append(c)
          }
        }
        if (out.nonEmpty) add(Text(out.toString, marks))
      }
    }
  }
}
