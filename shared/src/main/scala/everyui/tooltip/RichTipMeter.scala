package everyui.tooltip

import everyui.theme.Tone

/** A meter of a `RichTip`: `label` at its start and `words` at its end ("21.8 cores"), over a bar filled `fraction` of
  * the way (0 to 1) in `tone`'s colour. */
case class RichTipMeter(label: String, fraction: Double, tone: Tone = Tone.Accent, words: String = "")
