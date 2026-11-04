package everyui.markdown

/** A piece of a paragraph, a heading or a table cell in a parsed Markdown document: words and what styles them. */
sealed trait MdInline

object MdInline {
  /** Words as they are, escapes and entities already undone. */
  case class Text(text: String) extends MdInline
  /** `*words*` or `_words_`: slanted. */
  case class Emphasis(content: List[MdInline]) extends MdInline
  /** `**words**` or `__words__`: bold. */
  case class Strong(content: List[MdInline]) extends MdInline
  /** `~~words~~`: struck through. */
  case class Strike(content: List[MdInline]) extends MdInline
  /** `` `code` ``: in a monospaced face. */
  case class CodeSpan(code: String) extends MdInline
  /** `[words](href "title")`, a reference to a definition, or an autolink. */
  case class Hyperlink(href: String, title: String, content: List[MdInline]) extends MdInline
  /** `![alt](src "title")`: a picture, `alt` its words for whatever cannot see it. */
  case class Picture(src: String, title: String, alt: String) extends MdInline
  /** One of the few HTML tags a rich-text document writes into its Markdown (`<u>`, `</u>`, `<img …>`: an underline,
    * a picture with a size), kept as it was written so the document reads it back; drawn as the words it is. */
  case class Tag(source: String) extends MdInline
  /** A line ending inside a paragraph: a space when it is drawn. */
  case object SoftBreak extends MdInline
  /** Two spaces or a backslash at a line's end: a new line. */
  case object HardBreak extends MdInline
  /** A paragraph's source not yet parsed into pieces: only between the two passes of `Markdown.parse`, never in a
    * document it answers. */
  case class Pending(source: String) extends MdInline

  /** The words in `content`, without their styles: an image's alt text, a heading's plain name. */
  def plain(content: List[MdInline]): String = content.map(plainOf).mkString

  private def plainOf(i: MdInline): String = i match {
    case Text(t) => t
    case Emphasis(c) => plain(c)
    case Strong(c) => plain(c)
    case Strike(c) => plain(c)
    case CodeSpan(c) => c
    case Hyperlink(_, _, c) => plain(c)
    case Picture(_, _, alt) => alt
    case SoftBreak => " "
    case HardBreak => "\n"
    case Tag(s) => s
    case Pending(s) => s
  }
}
