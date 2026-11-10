package everyui.markdown

import everyui.Color
import everyui.component.{Container, IconButton, NavigationUrl, RichText, ScrollContainer, TextView}
import everyui.component.types.{Semantics, TextDirection, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.i18n.Words
import everyui.kit.Highlight
import everyui.log.{AnsiText, LogTheme}
import everyui.render.Platform
import everyui.text.TextRun
import everyui.theme.Theme
import reactify.*

/** A code block of a `MarkdownView`: its lines kept as they are and coloured for its `language` (`Highlight`), on the
  * code wash, scrolled sideways where a line is longer than `width` rather than wrapped (the arrows scroll it once it
  * has the focus). Over it, the language's name and a button that copies the code, where the platform has a
  * clipboard. New code for it (`code`, as an answer streams in) is drawn in place.
  *
  * A terminal's output (`CodeBlockView.terminal`: its escapes, or a language of "ansi" or "terminal") is drawn as a
  * terminal draws it, its SGR colours and attributes in the theme's terminal palette (`AnsiText`, `LogTheme`) over
  * any highlighting, an OSC 8 link kept as a link and every other escape dropped; the label is the language the
  * block was given, none where it was given none, and what is copied is the words without their escapes. */
class CodeBlockView(val language: String, source: String, ctx: MarkdownContext, width: => Double) extends Container {
  /** The code it shows. */
  val code: Var[String] = own(Var(source))
  private val pad = 12.0
  private val points = ctx.style.codePoints
  private val header = if (language.nonEmpty || Platform.clipboard()) 30.0 else 0.0

  private val name = TextView(language, points * 0.85, Theme.muted())
  private val copy = new IconButton(MaterialIcon.ContentCopy, Words("markdown.copy"), () => Platform.copyText(copied))
  private val text = new RichText {
    font.family @= "Roboto Mono"
    font.size @= points
    whiteSpace @= WhiteSpace.Pre
    color := Theme.text()
    // code reads left to right whatever the words about it do
    direction @= TextDirection.LeftToRight
    runs := CodeBlockView.runs(code(), language)
  }
  // the room after the longest line, as there is before it
  private val end = new Container {
    size.width @= pad
    size.height @= 1.0
  }
  // the ground it is on: the first fill of those it is in (a bubble, a card), none where nothing it is in is filled
  private def beneath(): Option[Color] = {
    var at = parent()
    var found: Option[Color] = None
    while (found.isEmpty && at.nonEmpty) {
      val fill = at.get.backgroundColor()
      if (fill.alpha > 0.0) found = Some(fill) else at = at.get.parent()
    }
    found
  }
  // code runs from its left in every language
  private val scroll = new ScrollContainer(horizontal = true, followsDirection = false)

  size.width := width
  // the wash: as whole as the app says (`MarkdownView.codeOpacity`), else see-through as the theme's surfaces are, by
  // the ground it is on, and whole on none (where it could be straight over a picture, and nothing says how much of
  // one: drawn as on a ground that lets nothing through)
  backgroundColor := (if (MarkdownView.codeOpacity().isEmpty && Theme.surfaceOpacity() >= 1.0) MarkdownView.codeColour()
    else MarkdownView.codeOver(beneath().getOrElse(Color.Black)))
  radius @= 8.0
  semantics @= Semantics("code", label = language)
  name.position.left @= pad
  name.position.top := (header - name.size.height()) / 2.0
  name.visible @= language.nonEmpty
  // what is over the code is not of it
  name.copyAs @= "none"
  copy.copyAs @= "none"
  copy.visible @= Platform.clipboard()
  copy.position.left := size.width() - copy.size.width() - 4.0
  copy.position.top @= 0.0
  text.position.left @= pad
  text.position.top @= (if (header > 0.0) 0.0 else pad)
  end.position.left := text.position.left() + text.size.width()
  scroll.content.children ++= List(text, end)
  scroll.position.top @= header
  scroll.size.width := size.width()
  scroll.size.height := text.position.top() + text.size.height() + pad
  // the arrows scroll it only where there is somewhere to scroll to
  scroll.focusable := text.size.width() + pad * 2.0 > scroll.size.width()
  size.height := header + scroll.size.height()
  // a terminal's links go where the view's other links go
  text.follow = href => ctx.press(href, this)
  children ++= List(scroll, name, copy)

  /** What its copy button puts on the clipboard: the code, its escapes taken out where it is a terminal's. */
  def copied: String = CodeBlockView.plain(language, code())

  override def kind: String = "CodeBlockView(" + language + ")"
}

object CodeBlockView {
  /** Whether code in `language` is a terminal's output, drawn as a terminal draws it: it holds escapes (ESC [ or
    * ESC ], `AnsiText.escaped`), whatever its language, or its language is "ansi" or "terminal". A "console" block
    * with escapes is one; without them it is coloured as a shell's, as it was. */
  def terminal(language: String, code: String): Boolean = AnsiText.escaped(code) || {
    val l = language.trim.toLowerCase
    l == "ansi" || l == "terminal"
  }

  /** `code` as it reads, for the clipboard and for plain text and HTML (`MarkdownPlain`, `MarkdownHtml`): a
    * terminal's without its escapes (`AnsiText.strip`), anything else as it is. */
  def plain(language: String, code: String): String = if (terminal(language, code)) AnsiText.strip(code) else code

  /** `code` as runs: a terminal's in the theme's terminal palette on the code wash, a link drawn as the view's
    * other links are (`MarkdownView.link`, underlined) where it is safe to follow (`NavigationUrl.safe`) and as its
    * words where not; anything else highlighted for `language` (`Highlight`). Read where the palette can change, it
    * follows the theme. */
  def runs(code: String, language: String): List[TextRun] =
    if (!terminal(language, code)) Highlight.runs(code, language)
    // (made to read on the wash, with as much of a picture through it as there could be)
    else AnsiText.runs(code, LogTheme.of(Theme.palette()).copy(background = MarkdownView.codeReadOn())).map { r =>
      if (r.href.isEmpty) r
      else if (NavigationUrl.safe(r.href)) r.copy(color = MarkdownView.link().toHex, underline = true)
      else r.copy(href = "")
    }
}
