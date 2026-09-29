package everyui.markdown

/** Markdown, parsed: CommonMark's blocks and inlines, with GFM's tables, strikethrough, task lists and bare links. Raw
  * HTML is kept as the text it is (`MdBlock.Html`, and `<` in a paragraph as a `<`), never rendered.
  *
  * {{{
  *   Markdown.parse("# Title\n\nSome *words*.") ==
  *     List(MdBlock.Heading(1, List(MdInline.Text("Title"))),
  *       MdBlock.Paragraph(List(MdInline.Text("Some "), MdInline.Emphasis(List(MdInline.Text("words"))), MdInline.Text("."))))
  * }}}
  *
  * A document that grows (an answer streaming in) is parsed again only from its last block by `MarkdownStream`. */
object Markdown {
  /** The blocks of `text`. */
  def parse(text: String): List[MdBlock] = {
    val run = BlockParser.parse(lines(text).lines)
    val refs = references(run.blocks.toList, Map.empty)
    run.blocks.toList.map(b => resolve(b, refs))
  }

  /** `text`'s lines (`\r\n` and `\r` ends as well as `\n`), the tabs in their indentation made spaces to the next
    * stop of four, and where each starts in `text`. */
  def lines(text: String): MdLines = {
    var out: Vector[String] = Vector.empty
    var starts: Vector[Int] = Vector.empty
    var at = 0
    var going = true
    while (going) {
      val end = text.indexOf("\n", at)
      val line = if (end < 0) text.substring(at) else text.substring(at, end)
      starts = starts :+ at
      out = out :+ expand(if (line.endsWith("\r")) line.substring(0, line.length - 1) else line)
      if (end < 0) going = false else at = end + 1
    }
    MdLines(out, starts)
  }

  /** `line` with the tabs among its leading spaces made spaces, to the next multiple of four. */
  private def expand(line: String): String =
    if (line.indexOf("\t") < 0) line
    else {
      val out = new StringBuilder
      var k = 0
      var lead = true
      while (k < line.length) {
        val c = line.charAt(k)
        if (lead && c == '\t') {
          out.append(' ')
          while (out.length % 4 != 0) out.append(' ')
        }
        else {
          if (c != ' ' && c != '>') lead = false
          out.append(c)
        }
        k += 1
      }
      out.toString
    }

  /** The link reference definitions in `blocks`, quotes and lists too, added to `found` (the first of a label wins). */
  def references(blocks: List[MdBlock], found: Map[String, MdReference]): Map[String, MdReference] = {
    var refs = found
    blocks.foreach {
      case MdBlock.Definitions(links) => links.foreach(r => if (!refs.contains(r.label)) refs = refs + (r.label -> r))
      case MdBlock.Quote(inner) => refs = references(inner, refs)
      case MdBlock.BulletList(_, _, _, items) => items.foreach(i => refs = references(i.blocks, refs))
      case _ => ()
    }
    refs
  }

  /** `block` with the words of its paragraphs, headings and cells parsed, references resolved by `refs`. */
  def resolve(block: MdBlock, refs: Map[String, MdReference]): MdBlock = block match {
    case MdBlock.Paragraph(content) => MdBlock.Paragraph(inlines(content, refs))
    case MdBlock.Heading(level, content) => MdBlock.Heading(level, inlines(content, refs))
    case MdBlock.Quote(inner) => MdBlock.Quote(inner.map(b => resolve(b, refs)))
    case MdBlock.BulletList(ordered, start, tight, items) =>
      MdBlock.BulletList(ordered, start, tight, items.map(i => MdItem(i.blocks.map(b => resolve(b, refs)), i.task)))
    case MdBlock.Table(align, header, rows) =>
      MdBlock.Table(align, header.map(c => inlines(c, refs)), rows.map(r => r.map(c => inlines(c, refs))))
    case other => other
  }

  private def inlines(content: List[MdInline], refs: Map[String, MdReference]): List[MdInline] =
    content.flatMap {
      case MdInline.Pending(source) => Inlines.parse(source, refs)
      case other => List(other)
    }
}
