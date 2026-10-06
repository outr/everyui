package everyui.component.types

import reactify.Var

/** What words do that are wider than their box, the room what they are in gives them (`TextView.textOverflow`):
  * kept in it (`Fit`, the default), wrapped where they wrap and cut short with an ellipsis where they are one line; or
  * drawn whole past it (`Visible`), as they were before words were kept to their box. Words that fit are drawn the same
  * either way. */
sealed abstract class TextOverflow(val value: String)

object TextOverflow {
  case object Fit extends TextOverflow("fit")
  case object Visible extends TextOverflow("visible")

  /** What a text view made from now on starts with: `Visible` for an app laid out on words running past their box. */
  val default: Var[TextOverflow] = Var(Fit)
}
