package everyui.markdown

import everyui.component.{Container, IconButton, RichText, ScrollContainer, TextView}
import everyui.component.types.{Semantics, TextDirection, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.i18n.Words
import everyui.kit.Highlight
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A code block of a `MarkdownView`: its lines kept as they are and coloured for its `language` (`Highlight`), on the
  * code wash, scrolled sideways where a line is longer than `width` rather than wrapped (the arrows scroll it once it
  * has the focus). Over it, the language's name and a button that copies the code, where the platform has a
  * clipboard. New code for it (`code`, as an answer streams in) is drawn in place. */
class CodeBlockView(val language: String, source: String, ctx: MarkdownContext, width: => Double) extends Container {
  /** The code it shows. */
  val code: Var[String] = own(Var(source))
  private val pad = 12.0
  private val points = ctx.style.codePoints
  private val header = if (language.nonEmpty || Platform.clipboard()) 30.0 else 0.0

  private val name = TextView(language, points * 0.85, Theme.muted())
  private val copy = new IconButton(MaterialIcon.ContentCopy, Words("markdown.copy"), () => Platform.copyText(code()))
  private val text = new RichText {
    font.family @= "Roboto Mono"
    font.size @= points
    whiteSpace @= WhiteSpace.Pre
    color := Theme.text()
    // code reads left to right whatever the words about it do
    direction @= TextDirection.LeftToRight
    runs := Highlight.runs(code(), language)
  }
  // the room after the longest line, as there is before it
  private val end = new Container {
    size.width @= pad
    size.height @= 1.0
  }
  // code runs from its left in every language
  private val scroll = new ScrollContainer(horizontal = true, followsDirection = false)

  size.width := width
  backgroundColor := MarkdownView.codeBackground()
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
  children ++= List(scroll, name, copy)

  override def kind: String = "CodeBlockView(" + language + ")"
}
