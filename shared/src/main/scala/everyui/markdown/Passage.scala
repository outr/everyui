package everyui.markdown

import everyui.Color
import everyui.theme.Tone

/** A passage of a `MarkdownView` to highlight (`MarkdownView.passages`), named by its words as they are written in the
  * Markdown: its first occurrence is drawn on a wash of `tone` with a line of it under, wherever its words are drawn
  * (paragraphs, headings, list items, quotes, table cells; never code blocks), across runs, lines and blocks. Its
  * words are found whatever the spaces and line breaks between them, and with or without the Markdown marks around
  * them (`**`, `*`, `_`, `~~`, `` ` ``, a link's brackets and address): `Passages.find`. */
case class Passage(text: String, tone: Tone = Tone.Warning)

object Passage {
  /** The wash behind a passage's letters: light enough that words in the theme's text colour read on it, in a light
    * theme and a dark one, over a bubble that lets a picture through as over one that does not. */
  def wash(tone: Tone): Color = tone.colour().withAlpha(0.24)

  /** The line under a passage: the tone itself. */
  def line(tone: Tone): Color = tone.colour()
}
