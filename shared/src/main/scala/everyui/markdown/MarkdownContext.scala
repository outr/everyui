package everyui.markdown

import everyui.component.types.TextDirection

/** What a `MarkdownView`'s blocks are drawn with: its style, what a link does, where relative pictures are found,
  * whether the words are quieter (in a quote), which way the words run (`direction`), whether each paragraph also
  * measures its lines on one line, for how wide the whole would be drawn (`measures`), whether a line break of the
  * text is a line break (`breaks`), whether a table sorts, filters, copies and exports (`richTables`), and how a table
  * is drawn (`tableLook`). */
private[everyui] case class MarkdownContext(style: MarkdownStyle, onLink: String => Unit, base: () => String,
                                            quiet: Boolean = false,
                                            direction: TextDirection = TextDirection.Inherit,
                                            measures: Boolean = false,
                                            breaks: Boolean = false,
                                            richTables: () => Boolean = () => true,
                                            tableLook: () => TableLook = () => TableLook()) {
  /** Whether `blocks` read right to left: by their words where the direction is `Auto`, else the direction's own or the
    * app's. */
  def rightToLeft(blocks: List[MdBlock]): Boolean =
    everyui.text.Bidi.rightToLeft(direction.value, MarkdownBlocks.words(blocks), everyui.i18n.Direction.rtl())
}
