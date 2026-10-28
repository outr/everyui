package everyui.component.types

import reactify.Var

/** What words do that are wider than their box, the room what they are in gives them, or than their `maxWidth`
  * (`TextView.textOverflow`): wrapped within it (`Wrap`, the default), a line that runs past it taking another, and a
  * word too long for a line broken where it must be; cut short with an ellipsis where they are one line (`Ellipsis`),
  * wrapping only where they wrap anyway; or drawn whole past it (`Visible`), as they were before words were kept to their
  * box. Words that fit are drawn the same every way, and `maxLines` cuts wrapped words with an ellipsis every way.
  *
  * Matt's rulings: words are kept to their box (2026-10-05), and wrap by default rather than end in an ellipsis, on
  * every platform (2026-10-27). An app that wants an ellipsis says so: on a view, or for every view it makes from then
  * on (`default`). */
sealed abstract class TextOverflow(val value: String)

object TextOverflow {
  case object Wrap extends TextOverflow("wrap")
  case object Ellipsis extends TextOverflow("ellipsis")
  case object Visible extends TextOverflow("visible")
  /** What words did before they wrapped by default: `Ellipsis`. */
  val Fit: TextOverflow = Ellipsis

  /** What a text view made from now on starts with: `Wrap`, unless the app wants `Ellipsis` everywhere (or `Visible`,
    * for one laid out on words running past their box). */
  val default: Var[TextOverflow] = Var(Wrap)
}
