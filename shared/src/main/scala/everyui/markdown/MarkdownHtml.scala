package everyui.markdown

/** Markdown as clean HTML, for a clipboard's rich flavour (`Clipboard.copyMarkdown`): only what the words are —
  * paragraphs, headings, strong and emphasis, lists, links, code, quotes, rules and tables — with no style, class,
  * colour or font of its own, so an email or a document pastes it in its own look. Everything it holds is escaped; raw
  * HTML in the Markdown comes out as the text it is, as `MarkdownView` shows it. A code block of a terminal's output
  * is its words without their escapes (`CodeBlockView.plain`), uncoloured as every other block is.
  *
  * With `breaks`, a line break in the text is a line break, as a chat's messages have it (`MessageMarkdown`).
  *
  * {{{
  *   MarkdownHtml("**Hi** <you>") == "<p><strong>Hi</strong> &lt;you&gt;</p>"
  * }}} */
object MarkdownHtml {
  /** `markdown` parsed and made HTML. */
  def apply(markdown: String, breaks: Boolean = false): String = blocks(Markdown.parse(markdown), breaks)

  /** `list` as HTML, a block to a line. */
  def blocks(list: List[MdBlock], breaks: Boolean = false): String =
    list.map(b => block(b, breaks)).filter(_.nonEmpty).mkString("\n")

  /** `content` as HTML: the words of a paragraph, a heading or a cell. */
  def inlines(content: List[MdInline], breaks: Boolean = false): String = content.map(i => piece(i, breaks)).mkString

  /** `text` with `&`, `<`, `>` and `"` escaped, for HTML's words and its attributes' values. */
  def escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private def block(b: MdBlock, breaks: Boolean): String = b match {
    case MdBlock.Paragraph(content) => "<p>" + inlines(content, breaks) + "</p>"
    case MdBlock.Heading(level, content) =>
      val tag = "h" + math.max(1, math.min(6, level)).toString
      "<" + tag + ">" + inlines(content, breaks) + "</" + tag + ">"
    case MdBlock.CodeBlock(language, code, _) =>
      "<pre><code>" + escape(MarkdownPlain.trimEnd(CodeBlockView.plain(language, code))) + "</code></pre>"
    case MdBlock.Html(source) => "<pre><code>" + escape(MarkdownPlain.trimEnd(source)) + "</code></pre>"
    case MdBlock.Quote(inside) => "<blockquote>\n" + blocks(inside, breaks) + "\n</blockquote>"
    case MdBlock.BulletList(ordered, start, tight, items) =>
      val open = if (!ordered) "<ul>" else if (start == 1) "<ol>" else "<ol start=\"" + start.toString + "\">"
      open + "\n" + items.map(i => item(i, tight, breaks)).mkString("\n") + "\n" + (if (ordered) "</ol>" else "</ul>")
    case MdBlock.Rule => "<hr>"
    case MdBlock.TableBlock(_, header, rows) =>
      val head = if (header.isEmpty) "" else "<thead>\n" + row(header, "th", breaks) + "\n</thead>\n"
      val body =
        if (rows.isEmpty) "" else "<tbody>\n" + rows.map(r => row(r, "td", breaks)).mkString("\n") + "\n</tbody>\n"
      "<table>\n" + head + body + "</table>"
    case MdBlock.Definitions(_) => ""
  }

  // a tight list's paragraphs are its items' words, with nothing around them
  private def item(it: MdItem, tight: Boolean, breaks: Boolean): String = {
    val inside = it.blocks.map {
      case MdBlock.Paragraph(content) if tight => inlines(content, breaks)
      case other => block(other, breaks)
    }.filter(_.nonEmpty).mkString("\n")
    "<li>" + MarkdownPlain.task(it) + inside + "</li>"
  }

  private def row(cells: List[List[MdInline]], tag: String, breaks: Boolean): String =
    "<tr>" + cells.map(c => "<" + tag + ">" + inlines(c, breaks) + "</" + tag + ">").mkString + "</tr>"

  private def piece(i: MdInline, breaks: Boolean): String = i match {
    case MdInline.Text(t) => escape(t)
    case MdInline.Emphasis(c) => "<em>" + inlines(c, breaks) + "</em>"
    case MdInline.Strong(c) => "<strong>" + inlines(c, breaks) + "</strong>"
    case MdInline.Strike(c) => "<del>" + inlines(c, breaks) + "</del>"
    case MdInline.CodeSpan(code) => "<code>" + escape(code) + "</code>"
    case MdInline.Hyperlink(href, _, c) =>
      if (everyui.component.NavigationUrl.safe(href)) "<a href=\"" + escape(href) + "\">" + inlines(c, breaks) + "</a>"
      else inlines(c, breaks)
    case MdInline.Picture(src, _, alt) => "<img src=\"" + escape(src) + "\" alt=\"" + escape(alt) + "\">"
    case MdInline.SoftBreak => if (breaks) "<br>\n" else "\n"
    case MdInline.HardBreak => "<br>\n"
    case MdInline.Tag(source) => escape(source)
    case MdInline.Pending(source) => escape(source)
  }
}
