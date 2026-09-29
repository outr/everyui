package everyui.chat

import everyui.component.{Component, Container}
import everyui.component.types.TextDirection
import everyui.markdown.{MarkdownStyle, MarkdownView}
import reactify.*

/** A message's words as Markdown (`everyui.markdown.MarkdownView`, in its compact style): what a transcript shows a
  * message with unless it is given another way to (`Transcript.render`; `MessageText.plain` for plain text).
  *
  * It is as wide as its widest line, up to `widest`, so a short reply makes a narrow bubble, while a code block, a
  * table, a rule or a picture takes all of `widest`. A line break in the text is a line break, as people typing a
  * message mean it, where Markdown would join the lines. Each paragraph, list and quote runs the way its own letters do
  * (`direction`, `TextDirection.Auto` unless it is given), code left to right, and a word too long for a line (a URL, a
  * path) is broken where it must be. As a message streams in, only its last block is parsed and drawn again. A click on
  * a link (or Enter on a paragraph that holds one) goes to `onLink`.
  *
  * {{{
  *   val transcript = new Transcript(entries, render = (text, widest) => new MessageMarkdown(text, widest,
  *     onLink = url => openInBrowser(url)))
  * }}} */
class MessageMarkdown(text: Val[String], widest: Val[Double], direction: TextDirection = TextDirection.Auto,
                      onLink: String => Unit = MarkdownView.follow) extends Container {
  val view: MarkdownView = new MarkdownView(text(), MarkdownStyle.compact, onLink, direction = direction,
    measures = true, breaks = true)
  view.content := text()
  view.size.width := math.max(1.0, view.natural() match {
    case Some(across) => math.min(widest(), math.ceil(across) + 1.0)
    case None => widest()
  })
  size.width := view.size.width()
  size.height := view.size.height()
  children += view

  override def kind: String = "MessageMarkdown"
}

object MessageMarkdown {
  /** The way a transcript shows a message's words unless it is told otherwise: Markdown, as wide as its widest line up
    * to the widest a bubble's words may be. */
  def render(text: Val[String], widest: Val[Double]): Component = new MessageMarkdown(text, widest)
}
