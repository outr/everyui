package everyui.component.types

/** A stretch of a field's words, from `start` up to `end`, in characters of its `text` (the web's UTF-16 units, as
  * `String` counts them): nothing chosen where they are the same, the caret there. */
case class TextRange(start: Int, end: Int) {
  /** Whether nothing is chosen: the caret alone. */
  def collapsed: Boolean = start == end

  /** How many characters it holds. */
  def length: Int = end - start
}
