package everyui.markdown

import everyui.theme.Tone

/** Where a passage (`Passage`, the `passage`th of a view's) lies in a unit of a view's words, a line or a table cell:
  * from character `from` up to `to` of the unit's words as drawn, marked in `tone`. */
private[everyui] case class MarkSpan(from: Int, to: Int, passage: Int, tone: Tone)
