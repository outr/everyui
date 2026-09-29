package everyui.date

import everyui.event.{Key, KeyEvent}

/** Where a key moves the day a calendar has the focus on: Left and Right a day (mirrored in a right-to-left language,
  * where Left is the day after), Up and Down a week, Page Up and Page Down a month (a year with Shift), and Home and
  * End to the start and end of the week. None for any other key. */
object CalendarKeys {
  def target(from: CalendarDate, e: KeyEvent, rtl: Boolean, firstDay: Int): Option[CalendarDate] = {
    val on = if (rtl) -1 else 1
    if (e.key == Key.ArrowLeft) Some(from.plusDays(-on))
    else if (e.key == Key.ArrowRight) Some(from.plusDays(on))
    else if (e.key == Key.ArrowUp) Some(from.plusDays(-7))
    else if (e.key == Key.ArrowDown) Some(from.plusDays(7))
    else if (e.key == Key.PageUp) Some(if (e.shift) from.plusYears(-1) else from.plusMonths(-1))
    else if (e.key == Key.PageDown) Some(if (e.shift) from.plusYears(1) else from.plusMonths(1))
    else if (e.name == "Home") Some(from.startOfWeek(firstDay))
    else if (e.name == "End") Some(from.endOfWeek(firstDay))
    else None
  }

  /** Whether `e` is one of those keys. */
  def moves(e: KeyEvent): Boolean =
    e.key == Key.ArrowLeft || e.key == Key.ArrowRight || e.key == Key.ArrowUp || e.key == Key.ArrowDown ||
      e.key == Key.PageUp || e.key == Key.PageDown || e.name == "Home" || e.name == "End"
}
