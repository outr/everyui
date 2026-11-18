package everyui.push

import everyui.markdown.{CodeBlockView, MarkdownPlain, MdBlock, MdInline}
import everyui.text.TextRun

/** A push's Markdown (`PushFormat.Markdown`) as a notification shows it, read by the parser a chat's messages are read
  * by (`everyui.markdown.Markdown`), a line break in the text a line break as a chat has it: as styled runs
  * (`runs`), for a platform that styles a notification's words (Android), or as plain words (`plain`), for one that
  * does not (a browser) and for an app's server to send where the format is not heeded.
  *
  * The marks of bold, slant and strikethrough go, and code loses its backticks; a link is its words alone (where it
  * goes only where it has no words of its own), since a notification opens no link in its words; a picture is its alt
  * text. A heading is its words, a quote its words without the `>`, a code block its lines; a list's items start
  * "• " ("☐ " or "☑ " after it for a task) or their number, "1. ", an item's further lines and a list inside it stood
  * in under its words; a table is a line to a row, its cells apart by tabs; a rule is the blank line it stands for.
  * Paragraphs and other blocks have a blank line between them. A mark with no partner stays as it was written.
  *
  * {{{
  *   PushText.plain("**Done.** See [the log](https://x.io/7).\n\n- `build` passed\n- 2 warnings") ==
  *     "Done. See the log.\n\n• build passed\n• 2 warnings"
  * }}}
  *
  * The runs are bold (`weight` 700) for strong words and a heading, `italic` for emphasis, `strike` for
  * strikethrough, and in the family "monospace" for code; their words are `plain`'s, line breaks and all. */
object PushText {
  /** What starts an item of a list that is not numbered. */
  val bullet: String = "• "

  /** `markdown` as plain words. */
  def plain(markdown: String): String = runs(markdown).map(_.text).mkString

  /** `markdown` as styled runs, their words `plain`'s, merged where one is styled as the one before it. */
  def runs(markdown: String): List[TextRun] = {
    val lines = blocks(everyui.markdown.Markdown.parse(markdown), blank = true)
    TextRun.merged(lines.zipWithIndex.flatMap((l, i) => if (i == 0) l else TextRun("\n") :: l))
      .filter(_.text.nonEmpty)
  }

  private type Line = List[TextRun]

  // the lines of `list`, a blank line between blocks where `blank` (a loose list's item), none where not
  private def blocks(list: List[MdBlock], blank: Boolean): List[Line] = {
    val made = list.map(block).filter(_.nonEmpty)
    made.zipWithIndex.flatMap((b, i) => if (i > 0 && blank) Nil :: b else b)
  }

  private def block(b: MdBlock): List[Line] = b match {
    case MdBlock.Paragraph(content) => inlines(content, TextRun(""))
    case MdBlock.Heading(_, content) => inlines(content, TextRun("", weight = 700))
    case MdBlock.CodeBlock(language, code, _) => written(MarkdownPlain.trimEnd(CodeBlockView.plain(language, code)),
      TextRun("", family = "monospace"))
    case MdBlock.Html(source) => written(MarkdownPlain.trimEnd(source), TextRun(""))
    case MdBlock.Quote(inside) => blocks(inside, blank = true)
    case MdBlock.BulletList(ordered, start, tight, items) =>
      items.zipWithIndex.flatMap { (it, i) =>
        val mark = (if (ordered) (start + i).toString + ". " else bullet) + MarkdownPlain.task(it)
        val pad = mark.map(_ => ' ').mkString
        val body = blocks(it.blocks, !tight) match {
          case Nil => List(Nil)
          case lines => lines
        }
        val item = (TextRun(mark) :: body.head) :: body.tail.map(l => if (l.isEmpty) l else TextRun(pad) :: l)
        if (i > 0 && !tight) Nil :: item else item
      }
    case MdBlock.Rule => Nil
    case MdBlock.TableBlock(_, header, rows) =>
      val row = (cells: List[List[MdInline]], style: TextRun) => cells.zipWithIndex.flatMap { (c, i) =>
        val words = inlines(c, style).flatten
        if (i == 0) words else TextRun("\t") :: words
      }
      (if (header.isEmpty) Nil else List(row(header, TextRun("", weight = 700)))) ++ rows.map(r => row(r, TextRun("")))
    case MdBlock.Definitions(_) => Nil
  }

  // `text`'s lines, each a run in `style`
  private def written(text: String, style: TextRun): List[Line] =
    text.split("\n", -1).toList.map(l => if (l.isEmpty) Nil else List(style.copy(text = l)))

  // the lines of a paragraph's pieces, each piece in `style` and its own
  private def inlines(content: List[MdInline], style: TextRun): List[Line] = {
    var lines: List[Line] = Nil
    var line: Line = Nil
    def add(content: List[MdInline], style: TextRun): Unit = content.foreach {
      case MdInline.Text(t) => line = style.copy(text = t) :: line
      case MdInline.Emphasis(c) => add(c, style.copy(italic = true))
      case MdInline.Strong(c) => add(c, style.copy(weight = 700))
      case MdInline.Strike(c) => add(c, style.copy(strike = true))
      case MdInline.CodeSpan(code) => line = style.copy(text = code, family = "monospace") :: line
      case MdInline.Hyperlink(href, _, c) =>
        val words = MdInline.plain(c)
        if (words.trim.isEmpty || words == href) line = style.copy(text = href.stripPrefix("mailto:")) :: line
        else add(c, style)
      case MdInline.Picture(src, _, alt) => line = style.copy(text = if (alt.isEmpty) src else alt) :: line
      case MdInline.SoftBreak | MdInline.HardBreak =>
        lines = line.reverse :: lines
        line = Nil
      case MdInline.Tag(source) => line = style.copy(text = source) :: line
      case MdInline.Pending(source) => line = style.copy(text = source) :: line
    }
    add(content, style)
    (line.reverse :: lines).reverse
  }
}
