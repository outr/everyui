package everyui.date

/** A range as it is chosen on a calendar, one day at a time: nothing yet, a `start` waiting for its end, or both ends.
  * `choose` takes the next day chosen: the end, where a start waits and the day is not before it; otherwise a new
  * start. Until the end is chosen, `preview` is the range the day under the pointer or the focus would make.
  *
  * {{{
  *   RangeChoice().choose(sep1).choose(sep9).range         // Some(2026-09-01/2026-09-09)
  *   RangeChoice().choose(sep9).choose(sep1).start         // Some(2026-09-01): a day before the start starts again
  * }}} */
case class RangeChoice(start: Option[CalendarDate] = None, end: Option[CalendarDate] = None) {
  def choose(date: CalendarDate): RangeChoice = start match {
    case Some(s) if end.isEmpty && !date.isBefore(s) => RangeChoice(start, Some(date))
    case _ => RangeChoice(Some(date), None)
  }

  /** Both ends, once they are chosen. */
  def range: Option[DateRange] = (start, end) match {
    case (Some(s), Some(e)) => Some(DateRange.of(s, e))
    case _ => None
  }

  /** Whether a start waits for its end. */
  def waiting: Boolean = start.nonEmpty && end.isEmpty

  /** The range `at` would end, while a start waits and `at` is not before it. */
  def preview(at: Option[CalendarDate]): Option[DateRange] = (start, at) match {
    case (Some(s), Some(a)) if end.isEmpty && !a.isBefore(s) => Some(DateRange(s, a))
    case _ => None
  }

  /** How `date` is drawn, with the pointer or the focus on `at`. */
  def mark(date: CalendarDate, at: Option[CalendarDate]): DayMark = {
    val chosen = range
    val shown = chosen.orElse(preview(at))
    val in = shown.exists(r => r.contains(date))
    DayMark(selected = start.contains(date) || end.contains(date),
      before = in && !shown.exists(r => r.start == date),
      after = in && !shown.exists(r => r.end == date),
      preview = in && chosen.isEmpty)
  }
}

object RangeChoice {
  /** A range already chosen, or none. */
  def of(range: Option[DateRange]): RangeChoice = RangeChoice(range.map(_.start), range.map(_.end))
}
