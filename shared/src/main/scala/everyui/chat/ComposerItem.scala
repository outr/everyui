package everyui.chat

import everyui.font.MaterialIcon

/** One of the app's entries in a [[Composer]]'s "+" menu (`Composer.plusItems`), after its own (Upload files, Mention
  * someone): its words, a glyph before them where it has one, a line of detail under them, and what choosing it does.
  * `ComposerItem.separator` is a rule between groups. */
case class ComposerItem(label: String, icon: Option[MaterialIcon] = None, detail: String = "",
                        action: () => Unit = () => (), separator: Boolean = false)

object ComposerItem {
  /** A rule between groups of entries. */
  val separator: ComposerItem = ComposerItem("", separator = true)
}
