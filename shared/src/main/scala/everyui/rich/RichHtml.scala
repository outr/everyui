package everyui.rich

import everyui.rich.RichInline.{Image, Text}

/** A rich document as HTML (`RichDoc.html`): `p`, `h1` to `h3`, `ul` and `ol` nested as deep as the items are, each
  * item an `li`, consecutive quotes one `blockquote`, `pre><code` with its language as a class, `hr` and `img`;
  * marks as `strong`, `em`, `u`, `s`, `code` and `a`, a line break as `br`. Every word and address is escaped, and an
  * address that is not a page, mail or a picture's (`javascript:`) is left out. */
object RichHtml {
  def write(doc: RichDoc): String = {
    val out = new StringBuilder
    // the lists open, innermost last, each with its tag
    var lists: List[String] = Nil
    var quoted = false
    def closeLists(to: Int): Unit = while (lists.length > to) {
      out.append("</li></" + lists.head + ">")
      lists = lists.tail
    }
    doc.blocks.foreach { b =>
      if (b.kind != BlockKind.Quote && quoted) {
        out.append("</blockquote>")
        quoted = false
      }
      // a list item: a nested list opened inside the item above it, which stays open
      def item(tag: String, depth: Int): Unit = {
        closeLists(depth + 1)
        if (lists.length == depth + 1 && lists.head != tag) closeLists(depth)
        if (lists.length == depth + 1) out.append("</li><li>")
        else while (lists.length < depth + 1) {
          out.append("<" + tag + "><li>")
          lists = tag :: lists
        }
        out.append(inlines(b.inlines))
      }
      b.kind match {
        case BlockKind.Bullet(d) => item("ul", d)
        case BlockKind.Numbered(d) => item("ol", d)
        case _ => closeLists(0)
      }
      b.kind match {
        case BlockKind.Paragraph => out.append("<p>" + inlines(b.inlines) + "</p>")
        case BlockKind.Heading(level) =>
          val h = "h" + math.max(1, math.min(3, level)).toString
          out.append("<" + h + ">" + inlines(b.inlines) + "</" + h + ">")
        case BlockKind.Quote =>
          if (!quoted) out.append("<blockquote>")
          quoted = true
          out.append("<p>" + inlines(b.inlines) + "</p>")
        case BlockKind.Code(language) =>
          val named = if (language.isEmpty) "" else " class=\"language-" + escape(language) + "\""
          out.append("<pre><code" + named + ">" +
            escape(b.text) + "</code></pre>")
        case BlockKind.Rule => out.append("<hr>")
        case BlockKind.Picture(image) => out.append("<p>" + picture(image) + "</p>")
        case _ => ()
      }
    }
    closeLists(0)
    if (quoted) out.append("</blockquote>")
    out.toString
  }

  private def inlines(pieces: Vector[RichInline]): String = pieces.map {
    case Text(t, marks) =>
      val words = escape(t).replace("\n", "<br>")
      val inner = if (marks.contains(Mark.Code)) "<code>" + words + "</code>" else words
      wrapped(inner, marks)
    case Image(image, marks) => wrapped(picture(image), marks)
  }.mkString

  private def wrapped(inner: String, marks: Set[Mark]): String = {
    val styled = List(Mark.Strike -> "s", Mark.Underline -> "u", Mark.Italic -> "em", Mark.Bold -> "strong")
      .foldLeft(inner) { case (s, (m, tag)) => if (marks.contains(m)) "<" + tag + ">" + s + "</" + tag + ">" else s }
    Mark.link(marks).filter(everyui.component.NavigationUrl.safe)
      .map(href => "<a href=\"" + escape(href) + "\">" + styled + "</a>").getOrElse(styled)
  }

  private def picture(image: RichImage): String = {
    val src = if (safeSource(image.source)) image.source else ""
    "<img src=\"" + escape(src) + "\" alt=\"" + escape(image.alt) + "\"" +
      image.width.map(w => " width=\"" + w.round.toString + "\"").getOrElse("") +
      image.height.map(h => " height=\"" + h.round.toString + "\"").getOrElse("") + ">"
  }

  private def safeSource(src: String): Boolean = {
    val lower = src.trim.toLowerCase
    !lower.startsWith("javascript:") && !lower.startsWith("vbscript:") &&
      (!lower.startsWith("data:") || lower.startsWith("data:image/"))
  }

  private def escape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
