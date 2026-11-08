package everyui.list

import everyui.Color
import everyui.theme.Theme

/** A usage bar on a `RichList` row (`RichLook.meter`): how full it is (`fraction`, 0..1, held there), and `words` at
  * the bar's end ("7.2 / 10 GB"), which a screen reader hears as the bar's value. The bar is in the accent, in the
  * warning colour from `warnAt` on, and in the danger colour from `dangerAt` on: a quota nearly used, then used up. */
case class RichMeter(fraction: Double, words: String = "", warnAt: Double = RichMeter.warnAt,
                     dangerAt: Double = RichMeter.dangerAt) {
  /** How full it is, held to 0..1 (nothing where it is not a number). */
  def filled: Double = if (fraction.isNaN) 0.0 else math.max(0.0, math.min(1.0, fraction))

  /** The bar's colour at how full it is: the danger colour from `dangerAt`, the warning colour from `warnAt`, else the
    * accent. */
  def tone: Color =
    if (filled >= dangerAt) Theme.danger() else if (filled >= warnAt) Theme.warning() else Theme.accent()

  /** What a screen reader hears of it: its words, or how full it is where it has none ("72%"). */
  def said: String = if (words.nonEmpty) words else math.round(filled * 100.0).toString + "%"
}

object RichMeter {
  /** Where a meter turns to the warning colour unless it says otherwise: three quarters full. */
  val warnAt: Double = 0.75
  /** Where it turns to the danger colour unless it says otherwise: nine tenths full. */
  val dangerAt: Double = 0.9
}
