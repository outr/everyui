package everyui.markdown

import everyui.component.types.TextDirection

/** What a `MarkdownView`'s blocks are drawn with: its style, what a link does, where relative pictures are found,
  * whether the words are quieter (in a quote), which way the words run (`direction`), whether each paragraph also
  * measures its lines on one line, for how wide the whole would be drawn (`measures`), whether a line break of the
  * text is a line break (`breaks`), whether a table sorts, filters, copies and exports (`richTables`), and how a table
  * is drawn (`tableLook`), the names a mention of is drawn as one (`mentions`, read where a line's runs are bound, so
  * they follow it), and what a press on one does (`onMention`, with the name and what it was pressed in; a mention is
  * pressed only where `pressesMentions`). */
private[everyui] case class MarkdownContext(style: MarkdownStyle, onLink: String => Unit, base: () => String,
                                            quiet: Boolean = false,
                                            direction: TextDirection = TextDirection.Inherit,
                                            measures: Boolean = false,
                                            breaks: Boolean = false,
                                            richTables: () => Boolean = () => true,
                                            tableLook: () => TableLook = () => TableLook(),
                                            mentions: () => Set[String] = () => Set.empty,
                                            pressesMentions: () => Boolean = () => false,
                                            onMention: (String, everyui.component.Component) => Unit = (_, _) => ()) {
  /** `href` followed from `at`: a mention's link (`Mentions.href`) answered by `onMention`, anything else by
    * `onLink`. */
  def press(href: String, at: everyui.component.Component): Unit = Mentions.pressed(href) match {
    case Some(name) => onMention(name, at)
    case None => onLink(href)
  }

  /** Whether `blocks` read right to left: by their words where the direction is `Auto`, else the direction's own or the
    * app's. */
  def rightToLeft(blocks: List[MdBlock]): Boolean =
    everyui.text.Bidi.rightToLeft(direction.value, MarkdownBlocks.words(blocks), everyui.i18n.Direction.rtl())
}
