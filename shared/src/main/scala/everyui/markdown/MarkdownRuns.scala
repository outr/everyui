package everyui.markdown

import everyui.emoji.CustomEmoji
import everyui.text.TextRun
import everyui.theme.Theme

/** A paragraph's pieces as the runs a `RichText` draws: emphasis slanted, strong bold, strikethrough struck, code in
  * the monospaced face on its wash, links in the link colour and underlined, and a mention of one of `mentions`
  * (`Mentions`) in the mention colour on its wash, outside code and links. A hard break starts a new line and a
  * picture stands on a line of its own (`MdPart`). */
private[markdown] object MarkdownRuns {
  /** The lines of `content`, set at `points` with `weight` as the words' own; with `breaks`, each line break of the
    * text starts a new line too, as a chat's messages have it; "@" and one of `mentions` drawn as a mention, and as a
    * link to the name where it is `pressable` (`Mentions.href`). */
  def parts(content: List[MdInline], points: Double, weight: Int, breaks: Boolean = false,
            mentions: Set[String] = Set.empty, pressable: Boolean = false): List[MdPart] = {
    val out = new MarkdownRuns(points, breaks, mentions, pressable)
    out.add(content, TextRun("", weight = weight))
    out.done()
  }

  /** The links in `content`: each one's words and where it goes. */
  def links(content: List[MdInline]): List[(String, String)] = {
    var out: List[(String, String)] = Nil
    content.foreach {
      case MdInline.Hyperlink(href, _, inside) => out = (MdInline.plain(inside), href) :: out
      case MdInline.Emphasis(inside) => out = links(inside).reverse ::: out
      case MdInline.Strong(inside) => out = links(inside).reverse ::: out
      case MdInline.Strike(inside) => out = links(inside).reverse ::: out
      case _ => ()
    }
    out.reverse
  }

  /** `c` as a run's colour: `#rrggbb` where it is whole, as it always was, and `#rrggbbaa` where it is not (a code
    * span's wash over a picture, `MarkdownView.codeBackground`), which the web and Android read. */
  def hex(c: everyui.Color): String = if (c.alpha >= 1.0) c.toHex else c.toHexAlpha

}

/** Runs gathered a line at a time (`MarkdownRuns.parts`). */
private[markdown] class MarkdownRuns(points: Double, breaks: Boolean, mentions: Set[String], pressable: Boolean) {
  private var lines: List[MdPart] = Nil
  private var line: List[TextRun] = Nil

  private def lineEnd(): Unit = {
    lines = MdPart(line.reverse, None) :: lines
    line = Nil
  }

  def done(): List[MdPart] = {
    lineEnd()
    // a picture's empty lines either side of it go
    lines.reverse.filter(p => p.image.nonEmpty || p.runs.exists(_.text.trim.nonEmpty))
  }

  def add(content: List[MdInline], style: TextRun): Unit =
    if (mentions.isEmpty && CustomEmoji.all().isEmpty) content.foreach(one(_, style)) else {
    var rest = content
    while (rest.nonEmpty) {
      // words that run on from one piece to the next taken together, so a mention or an emoji's name split between
      // them (`:party_parrot:`, cut at its underscore) is still one
      var words = ""
      var more = true
      while (more && rest.nonEmpty) rest.head match {
        case MdInline.Text(t) =>
          words = words + t
          rest = rest.tail
        case _ => more = false
      }
      if (words.nonEmpty) text(words, style)
      if (rest.nonEmpty) {
        one(rest.head, style)
        rest = rest.tail
      }
    }
  }

  // words, a mention in them drawn as one (not in a link, which is drawn as a link), and the app's own emoji named in
  // them as their pictures
  private def text(words: String, style: TextRun): Unit =
    if (mentions.isEmpty || style.href.nonEmpty) pictured(words, style)
    else Mentions.split(words, mentions).foreach { case (part, named) =>
      if (named) line = style.copy(text = part, color = MarkdownView.mention().toHex,
        background = MarkdownView.mentionWash().toHexAlpha,
        href = if (pressable) Mentions.nameOf(part, mentions).map(Mentions.href).getOrElse("") else "") :: line
      else pictured(part, style)
    }

  // words with each of the app's own emoji they name (`CustomEmoji`) as its picture, its name its words
  private def pictured(words: String, style: TextRun): Unit =
    CustomEmoji.split(words).foreach { case (part, emoji) =>
      line = emoji.map(e => style.copy(text = part, image = e.source)).getOrElse(style.copy(text = part)) :: line
    }

  private def one(inline: MdInline, style: TextRun): Unit = inline match {
    case MdInline.Text(t) => text(t, style)
    case MdInline.SoftBreak => if (breaks) lineEnd() else line = style.copy(text = " ") :: line
    case MdInline.HardBreak => lineEnd()
    case MdInline.Tag(source) => text(source, style)
    case MdInline.Emphasis(inside) => add(inside, style.copy(italic = true))
    case MdInline.Strong(inside) => add(inside, style.copy(weight = 700))
    case MdInline.Strike(inside) => add(inside, style.copy(strike = true))
    case MdInline.CodeSpan(code) => line = style.copy(text = code, family = "Roboto Mono", size = points * 0.88,
      background = MarkdownRuns.hex(MarkdownView.codeBackground())) :: line
    case MdInline.Hyperlink(href, title, inside) =>
      add(inside, style.copy(href = href, title = title, color = MarkdownView.link().toHex, underline = true))
    case i: MdInline.Picture =>
      lineEnd()
      lines = MdPart(Nil, Some(i)) :: lines
    case MdInline.Pending(source) => line = style.copy(text = source) :: line
  }
}
