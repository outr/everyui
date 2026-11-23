package everyui.tooltip

import everyui.font.MaterialIcon
import everyui.theme.Tone

/** A line of a `RichTip`: its icon in the theme's muted colour and its words in its text colour, or both in `tone`'s
  * (a warning, a success) where it has one. */
case class RichTipLine(icon: MaterialIcon, text: String, tone: Option[Tone] = None)
