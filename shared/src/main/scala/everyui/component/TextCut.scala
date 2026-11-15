package everyui.component

import everyui.component.types.{Elide, TextOverflow, WhiteSpace}

/** What words were last cut to their room by (`TextView.cut`): the same again, with the same words, is not cut again.
  * Kept as its values rather than joined into a string, which every text view held as a dozen pieces. */
private[component] case class TextCut(limit: Double, lines: Int, box: Double, own: Double, whiteSpace: WhiteSpace,
                                      overflow: TextOverflow, elide: Elide, sized: Boolean, width: Double,
                                      changes: Int)
