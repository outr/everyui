package everyui.component.types

/** Which way a text's words run: the app's reading direction (`Inherit`, `everyui.i18n.Direction`), the words' own
  * (`Auto`: the first letter that has a direction decides, as a web page's `dir="auto"` does, so an English message
  * reads left to right in an Arabic app and an Arabic one right to left in an English app), or one way whatever the
  * words are. It turns the words and the side a line starts from, never where the box sits: a layout still mirrors
  * with the app's direction. */
sealed abstract class TextDirection(val value: String)

object TextDirection {
  case object Inherit extends TextDirection("")
  case object Auto extends TextDirection("auto")
  case object LeftToRight extends TextDirection("ltr")
  case object RightToLeft extends TextDirection("rtl")
}
