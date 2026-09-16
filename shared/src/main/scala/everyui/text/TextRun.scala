package everyui.text

/** A stretch of text in one style, in a [[everyui.component.RichText]]: anything left empty or zero is the paragraph's
  * own. A run with a `background` is a marked span (code, say), a little inset; one with an `href` is a link. The
  * backends that draw no styled runs show the words in the paragraph's style. */
case class TextRun(text: String,
                   family: String = "",
                   weight: Int = 0,
                   italic: Boolean = false,
                   size: Double = 0.0,
                   color: String = "",
                   background: String = "",
                   strike: Boolean = false,
                   href: String = "")
