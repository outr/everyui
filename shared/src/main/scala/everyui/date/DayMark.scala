package everyui.date

/** How a calendar draws one day of a selection: `selected`, filled (a date chosen, or either end of a range); `before`
  * and `after`, the band of a range running on from it toward the day before and the day after (the band is drawn
  * across the day with both, from its middle with one); and `preview`, a band that shows what a range would be, with
  * its end not yet chosen. */
case class DayMark(selected: Boolean = false, before: Boolean = false, after: Boolean = false, preview: Boolean = false)

object DayMark {
  /** A day no selection touches. */
  val plain: DayMark = DayMark()
}
