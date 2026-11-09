package everyui.log

import everyui.{Color, PlatformKind}
import everyui.component.Component
import everyui.render.Platform
import everyui.text.TextRun

/** Text written for a terminal, whole rather than streamed, made into what a `RichText` draws or into the words it
  * reads as, outside a `LogView`: a coding agent's output in a chat, say. Its lines are read by an `AnsiParser`, the
  * style carried from one to the next as a terminal carries it, so the same SGR codes, OSC 8 links and dropped escapes
  * (the cursor's moves, erasing, the other OSC sequences) as a log view's; a sequence cut off at the end of a line, or
  * of the text, is dropped, never shown. `runs` colours them as `theme` does (its sixteen, the 256 and colours of their
  * own made to read on its ground where it is `readable`; bold, dim, italic, underline and strike; a ground of its own
  * as the run's background where `grounds`; a link as its `href`), each line's end a plain "\n", runs next to each
  * other in one style joined; `strip` gives the words alone, a line to a line. Where the platform draws no ground
  * behind a run (`AnsiText.grounds`: the desktop, a Roku and an Apple TV), a reversed run is drawn as it would be
  * unreversed and grounds are left out, so no words are drawn in the colour of the ground they would have been on.
  *
  * {{{
  *   note.runs := AnsiText.runs(output(), LogTheme.of(Theme.palette()))
  *   Platform.copyText(AnsiText.strip(output()))
  * }}} */
object AnsiText {
  /** `text` coloured for `theme`, as runs for a `RichText` (its `whiteSpace` `Pre`, so its lines stay as they are),
    * with the runs' grounds where `grounds`. */
  def runs(text: String, theme: LogTheme, grounds: Boolean = AnsiText.grounds): List[TextRun] = {
    val newline = TextRun("\n")
    // newest first
    var out: List[TextRun] = Nil
    var first = true
    lines(text).foreach { line =>
      if (!first) out = joined(out, newline)
      first = false
      LogRow.covering(line.text, line.spans).foreach { span =>
        if (span.to > span.from || out.isEmpty) {
          val shown = if (grounds) span else span.copy(style = span.style.copy(reverse = false, bg = AnsiStyle.Default))
          out = joined(out, run(line.text.substring(span.from, span.to), shown, theme, grounds))
        }
      }
    }
    out.reverse
  }

  /** Whether the platform draws a run's ground (`TextRun.background`): the web and Android do; the desktop's
    * JavaFX, a Roku and an Apple TV do not. */
  def grounds: Boolean = Platform.kind == PlatformKind.Web || Platform.kind == PlatformKind.Android

  /** `text` with every escape and control character taken out, a line to a line: its words as a terminal shows them. */
  def strip(text: String): String = lines(text).map(_.text).mkString("\n")

  /** Whether `text` holds a terminal's escapes: a control sequence or an operating system command (ESC [ or ESC ],
    * or their 8-bit forms). */
  def escaped(text: String): Boolean =
    text.indexOf("\u001b[") >= 0 || text.indexOf("\u001b]") >= 0 || text.indexOf("\u009b") >= 0 ||
      text.indexOf("\u009d") >= 0

  /** `text`'s lines read, each `\n` ending one, the last whatever follows the last `\n`. */
  def lines(text: String): List[AnsiLine] = {
    val parser = new AnsiParser
    var out: List[AnsiLine] = Nil
    var from = 0
    var nl = text.indexOf("\n")
    while (nl >= 0) {
      out = parser.line(text.substring(from, nl)) :: out
      from = nl + 1
      nl = text.indexOf("\n", from)
    }
    out = parser.line(text.substring(from)) :: out
    out.reverse
  }

  /** `text`, a stretch of `span`, as a run drawn as `theme` draws it: its ground as the run's background only where
    * `grounds` (a `LogRow` draws grounds as boxes of its own). */
  private[log] def run(text: String, span: LogSpan, theme: LogTheme, grounds: Boolean): TextRun =
    if (span.style.plain && span.href.isEmpty) TextRun(text)
    else {
      val ground = if (grounds) theme.ground(span.style).map(c => Component.css(c)).getOrElse("") else ""
      TextRun(text, color = Component.css(letters(span.style, theme)), weight = if (span.style.bold) 700 else 0,
        italic = span.style.italic, strike = span.style.strike, underline = span.style.underline, href = span.href,
        background = ground)
    }

  /** The colour of letters in `style`: a concealed run's in its ground, so they hide but are still there to copy. */
  private[log] def letters(style: AnsiStyle, theme: LogTheme): Color =
    if (style.conceal) theme.ground(style.copy(conceal = false)).getOrElse(theme.background)
    else theme.letters(style)

  // `r` added to `out` (newest first), or joined to the run before it where the two are in one style
  private def joined(out: List[TextRun], r: TextRun): List[TextRun] = out match {
    case last :: rest if last.copy(text = "") == r.copy(text = "") => last.copy(text = last.text + r.text) :: rest
    case _ => r :: out
  }
}
