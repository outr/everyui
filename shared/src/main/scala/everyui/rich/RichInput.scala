package everyui.rich

/** What a platform's editing surface for a rich document (`Nodes.richEditor`) says the person did, in the document's
  * places: never applied by the surface itself, but by the editor (`RichTextEditor`), which draws the document again.
  * A surface asks nothing of the app's model; it only reports. */
sealed trait RichInput

object RichInput {
  /** Words typed over `range`: keys, an input method's composition as it ends, a spelling suggestion taken,
    * dictation. */
  case class Typed(range: RichRange, words: String) extends RichInput
  /** `range` deleted; where nothing is chosen, the character (or with `word`, the word) before the caret
    * (`backward`) or after it. */
  case class Deleted(range: RichRange, backward: Boolean, word: Boolean = false) extends RichInput
  /** Enter: the block split at `range`. */
  case class Split(range: RichRange) extends RichInput
  /** Shift+Enter: a line break inside the block. */
  case class LineBreak(range: RichRange) extends RichInput
  /** A paste or a drop of words over `range`: as HTML where the clipboard has it ("" where not), and as plain text. */
  case class Pasted(range: RichRange, html: String, text: String) extends RichInput
  /** Pictures pasted or dropped at `range`: each one's type and bytes. */
  case class Pictures(range: RichRange, pictures: List[(String, Array[Byte])]) extends RichInput
  /** What is chosen now, as the person moved the caret or chose words. */
  case class Selected(range: RichRange) extends RichInput
  /** A command the platform sends for its own keys or menus: "undo", "redo", "bold", "italic", "underline". */
  case class Command(name: String) extends RichInput
  /** A press on a link, with the address it goes to. */
  case class LinkPressed(href: String) extends RichInput
}
