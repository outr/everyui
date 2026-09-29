package everyui.date

/** The days from `start` to `end`, both included; `start` is never after `end` when made by `DateRange.of`.
  *
  * {{{
  *   val week = DateRange.of(today, today.plusDays(-6))    // the last seven days, in order
  *   week.days                                            // 7
  *   week.contains(today.plusDays(-3))                    // true
  * }}} */
case class DateRange(start: CalendarDate, end: CalendarDate) {
  /** How many days it holds, both ends counted. */
  def days: Int = start.daysUntil(end) + 1

  def contains(date: CalendarDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

  /** Whether it is a single day. */
  def single: Boolean = start == end

  /** It with its ends in order. */
  def normalized: DateRange = if (end.isBefore(start)) DateRange(end, start) else this

  /** `start/end` in ISO 8601, as an interval is written: `2026-09-01/2026-09-29`. */
  def iso: String = start.iso + "/" + end.iso

  override def toString: String = iso
}

object DateRange {
  /** The days between `a` and `b`, whichever comes first. */
  def of(a: CalendarDate, b: CalendarDate): DateRange = if (b.isBefore(a)) DateRange(b, a) else DateRange(a, b)

  /** An ISO 8601 interval of two dates, `2026-09-01/2026-09-29`, in order. */
  def parse(text: String): Option[DateRange] = {
    val at = text.indexOf("/")
    if (at < 0) None
    else (CalendarDate.parse(text.substring(0, at)), CalendarDate.parse(text.substring(at + 1))) match {
      case (Some(a), Some(b)) if !b.isBefore(a) => Some(DateRange(a, b))
      case _ => None
    }
  }
}
