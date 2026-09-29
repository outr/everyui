package everyui.markdown

/** What a `MarkdownView`'s blocks are drawn with: its style, what a link does, where relative pictures are found, and
  * whether the words are quieter (in a quote). */
private[markdown] case class MarkdownContext(style: MarkdownStyle, onLink: String => Unit, base: () => String,
                                             quiet: Boolean = false)
