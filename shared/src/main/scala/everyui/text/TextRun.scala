package everyui.text

/** A stretch of text in one style, in a [[everyui.component.RichText]]: anything left empty or zero is the paragraph's
  * own. A run with a `background` is a marked span (code, say), a little inset; one with an `href` is a link; `strike`
  * and `underline` draw a line through or under its letters; `letterSpacing` (pixels) is its own spacing, where the
  * paragraph's would be (`FontFeature.letterSpacing`). The backends that draw no styled runs show the words in
  * the paragraph's style (JavaFX underlines a paragraph only where all of it is underlined; a Roku's label has no
  * underline at all). A run with an `image` (an address) is that picture in place of its words, a square the height of
  * its letters with the picture fitted in it, its `text` read out and copied for it (a custom emoji's `:name:`); a
  * backend that draws no styled runs shows the words. `title` is what it says as the pointer rests on it and, after
  * its words, to a screen reader (a Markdown link's `"title"`; a link's by `Link.describe` where it has none of its
  * own): the web's `title` and `aria-description`, the platform's tooltip on the desktop and Android, nothing on a TV. */
case class TextRun(text: String,
                   family: String = "",
                   weight: Int = 0,
                   italic: Boolean = false,
                   size: Double = 0.0,
                   color: String = "",
                   background: String = "",
                   strike: Boolean = false,
                   href: String = "",
                   underline: Boolean = false,
                   letterSpacing: Option[Double] = None,
                   image: String = "",
                   title: String = "")

object TextRun {
  /** How big a run's picture (`image`) is, a square, in times the size of its letters. */
  val pictureEm: Double = 1.25
}
