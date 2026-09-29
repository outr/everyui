package everyui.markdown

import everyui.text.TextRun
import everyui.theme.Theme

/** A paragraph's pieces as the runs a `RichText` draws: emphasis slanted, strong bold, strikethrough struck, code in
  * the monospaced face on its wash, links in the link colour and underlined. A hard break starts a new line and a
  * picture stands on a line of its own (`MdPart`). */
private[markdown] object MarkdownRuns {
  /** The lines of `content`, set at `points` with `weight` as the words' own. */
  def parts(content: List[MdInline], points: Double, weight: Int): List[MdPart] = {
    val out = new MarkdownRuns(points)
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

}

/** Runs gathered a line at a time (`MarkdownRuns.parts`). */
private[markdown] class MarkdownRuns(points: Double) {
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

  def add(content: List[MdInline], style: TextRun): Unit = content.foreach {
    case MdInline.Text(t) => line = style.copy(text = t) :: line
    case MdInline.SoftBreak => line = style.copy(text = " ") :: line
    case MdInline.HardBreak => lineEnd()
    case MdInline.Emphasis(inside) => add(inside, style.copy(italic = true))
    case MdInline.Strong(inside) => add(inside, style.copy(weight = 700))
    case MdInline.Strike(inside) => add(inside, style.copy(strike = true))
    case MdInline.CodeSpan(code) => line = style.copy(text = code, family = "Roboto Mono", size = points * 0.88,
      background = MarkdownView.codeBackground().toHex) :: line
    case MdInline.Hyperlink(href, _, inside) =>
      add(inside, style.copy(href = href, color = MarkdownView.link().toHex, underline = true))
    case i: MdInline.Picture =>
      lineEnd()
      lines = MdPart(Nil, Some(i)) :: lines
    case MdInline.Pending(source) => line = style.copy(text = source) :: line
  }
}
