package everyui.markdown

/** Markdown as plain prose, for a clipboard's text flavour (`Clipboard.copyMarkdown`): what reads well pasted where
  * there are no styles. Paragraphs, headings and quotes are their words, a blank line between each; the marks of bold,
  * slant and code go; a list's items start "- " or "1. ", a list inside an item stood in under its words; a link is
  * its words and where it goes, "docs (https://…)", or only where it goes where those are its words; a code block is
  * its lines as they are (a terminal's output without its escapes, `CodeBlockView.plain`); a rule is the blank line
  * it stands for; a table is a line to a row, its cells apart by tabs.
  *
  * With `breaks`, a line break in the text is a line break, as a chat's messages have it (`MessageMarkdown`).
  *
  * {{{
  *   MarkdownPlain("**Hi**, see [the docs](https://x.io).\n\n- one\n- two") == "Hi, see the docs (https://x.io).\n\n- one\n- two"
  * }}} */
object MarkdownPlain {
  /** `markdown` parsed and made plain text. */
  def apply(markdown: String, breaks: Boolean = false): String = blocks(Markdown.parse(markdown), breaks)

  /** `list` as plain text, a blank line between blocks. */
  def blocks(list: List[MdBlock], breaks: Boolean = false): String = joined(list, breaks, "\n\n")

  /** `content` as plain text: the words of a paragraph, a heading or a cell. */
  def inlines(content: List[MdInline], breaks: Boolean = false): String = content.map(i => piece(i, breaks)).mkString

  private def joined(list: List[MdBlock], breaks: Boolean, between: String): String =
    list.map(b => block(b, breaks)).filter(_.nonEmpty).mkString(between)

  private def block(b: MdBlock, breaks: Boolean): String = b match {
    case MdBlock.Paragraph(content) => inlines(content, breaks)
    case MdBlock.Heading(_, content) => inlines(content, breaks)
    case MdBlock.CodeBlock(language, code, _) => trimEnd(CodeBlockView.plain(language, code))
    case MdBlock.Html(source) => trimEnd(source)
    case MdBlock.Quote(inside) => blocks(inside, breaks)
    case MdBlock.BulletList(ordered, start, tight, items) =>
      val between = if (tight) "\n" else "\n\n"
      var out: List[String] = Nil
      var n = start
      items.foreach { it =>
        val mark = if (ordered) n.toString + ". " else "- "
        out = indented(mark + task(it), joined(it.blocks, breaks, between)) :: out
        n += 1
      }
      out.reverse.mkString(between)
    case MdBlock.Rule => ""
    case MdBlock.TableBlock(_, header, rows) =>
      (if (header.isEmpty) rows else header :: rows).map(r => r.map(c => inlines(c, breaks)).mkString("\t"))
        .mkString("\n")
    case MdBlock.Definitions(_) => ""
  }

  /** A task's box before its item's words: ☑ ticked, ☐ not; nothing for an item that is not a task. */
  def task(it: MdItem): String = it.task match {
    case Some(true) => "☑ "
    case Some(false) => "☐ "
    case None => ""
  }

  /** `text` less the line breaks at its end. */
  def trimEnd(text: String): String = {
    var end = text.length
    while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) end -= 1
    text.substring(0, end)
  }

  // `body` after `mark`, its lines past the first stood in as far as the mark is wide (a blank line left blank)
  private def indented(mark: String, body: String): String = {
    var pad = ""
    while (pad.length < mark.length) pad = pad + " "
    mark + body.split("\n", -1).toList.zipWithIndex.map { l =>
      if (l._2 == 0 || l._1.isEmpty) l._1 else pad + l._1
    }.mkString("\n")
  }

  private def piece(i: MdInline, breaks: Boolean): String = i match {
    case MdInline.Text(t) => t
    case MdInline.Emphasis(c) => inlines(c, breaks)
    case MdInline.Strong(c) => inlines(c, breaks)
    case MdInline.Strike(c) => inlines(c, breaks)
    case MdInline.CodeSpan(code) => code
    case MdInline.Hyperlink(href, _, c) =>
      val words = inlines(c, breaks)
      if (words.trim.isEmpty || words == href || "mailto:" + words == href) href.stripPrefix("mailto:")
      else words + " (" + href + ")"
    case MdInline.Picture(src, _, alt) => if (alt.isEmpty) src else alt
    case MdInline.SoftBreak => if (breaks) "\n" else " "
    case MdInline.HardBreak => "\n"
    case MdInline.Tag(source) => source
    case MdInline.Pending(source) => source
  }
}
