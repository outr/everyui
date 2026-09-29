package everyui.date

/** A range to choose with one press, beside a range picker's calendar: `label` is the app's words, and `range` the days
  * it means, worked out from today as it is chosen.
  *
  * {{{
  *   List(DatePreset.lastDays("Last 7 days", 7), DatePreset.lastDays("Last 30 days", 30),
  *     DatePreset.thisMonth("This month"), DatePreset.lastMonth("Last month"), DatePreset.yearToDate("This year"),
  *     DatePreset("Next weekend", today => ...))
  * }}} */
case class DatePreset(label: String, range: CalendarDate => DateRange)

object DatePreset {
  /** The `days` days up to and including today. */
  def lastDays(label: String, days: Int): DatePreset =
    DatePreset(label, today => DateRange(today.plusDays(1 - math.max(1, days)), today))

  /** Today alone. */
  def today(label: String): DatePreset = DatePreset(label, today => DateRange(today, today))

  /** The month so far: its first day to today. */
  def thisMonth(label: String): DatePreset = DatePreset(label, today => DateRange(today.firstOfMonth, today))

  /** The whole of the month before. */
  def lastMonth(label: String): DatePreset = DatePreset(label, today => {
    val before = today.firstOfMonth.plusMonths(-1)
    DateRange(before, before.lastOfMonth)
  })

  /** The year so far: January 1 to today. */
  def yearToDate(label: String): DatePreset = DatePreset(label, today => DateRange(CalendarDate(today.year, 1, 1), today))
}
