package everyui.date

import everyui.render.Platform

/** A day on the calendar, with no time and no zone: `month` is 1 to 12, `day` 1 to the month's length. The proleptic
  * Gregorian calendar, as ISO 8601 has it, for any year (0 is 1 BC). Worked out in plain arithmetic rather than
  * `java.time`, so it runs on every backend, a Roku too.
  *
  * The arithmetic goes through the day's number since 1970-01-01 (`epochDay`): adding days, weeks and months, the day of
  * the week (1 for Monday to 7 for Sunday, as `everyui.i18n.DateText` numbers them), and comparing. `toString` is the ISO
  * form, which `parse` reads back.
  *
  * {{{
  *   val due = CalendarDate(2026, 1, 31).plusMonths(1)      // 2026-02-28
  *   due.weekday                                           // 6: a Saturday
  *   CalendarDate.parse("2024-02-29").map(_.plusYears(1))  // Some(2025-02-28)
  *   CalendarDate.today().plusDays(-7)                     // a week ago, where the device is
  * }}} */
case class CalendarDate(year: Int, month: Int, day: Int) {
  /** Days since 1970-01-01, negative before it. */
  def epochDay: Int = CalendarDate.daysFromCivil(year, month, day)

  /** 1 for Monday to 7 for Sunday. */
  def weekday: Int = CalendarDate.floorMod(epochDay + 3, 7) + 1

  def isLeapYear: Boolean = CalendarDate.leapYear(year)
  def daysInMonth: Int = CalendarDate.daysInMonth(year, month)
  /** 1 to 366. */
  def dayOfYear: Int = epochDay - CalendarDate(year, 1, 1).epochDay + 1

  def plusDays(days: Int): CalendarDate = if (days == 0) this else CalendarDate.ofEpochDay(epochDay + days)
  def plusWeeks(weeks: Int): CalendarDate = plusDays(weeks * 7)
  /** `months` on, the day kept unless the month is shorter, where it is its last: January 31 and a month is February
    * 28 (29 in a leap year). */
  def plusMonths(months: Int): CalendarDate = if (months == 0) this else {
    val index = year * 12 + (month - 1) + months
    val y = CalendarDate.floorDiv(index, 12)
    val m = CalendarDate.floorMod(index, 12) + 1
    CalendarDate(y, m, math.min(day, CalendarDate.daysInMonth(y, m)))
  }
  /** `years` on, February 29 becoming the 28th outside a leap year. */
  def plusYears(years: Int): CalendarDate = plusMonths(years * 12)

  /** Days from this to `other`: positive when `other` is later. */
  def daysUntil(other: CalendarDate): Int = other.epochDay - epochDay

  /** Negative when this is earlier than `other`, 0 on the same day, positive when later. */
  def compare(other: CalendarDate): Int =
    if (year != other.year) year - other.year else if (month != other.month) month - other.month else day - other.day
  def isBefore(other: CalendarDate): Boolean = compare(other) < 0
  def isAfter(other: CalendarDate): Boolean = compare(other) > 0
  /** Whether it falls in `min` to `max`, each end included, an end not given being open. */
  def within(min: Option[CalendarDate], max: Option[CalendarDate]): Boolean =
    min.forall(m => !isBefore(m)) && max.forall(m => !isAfter(m))
  /** It, or the nearer of `min` and `max` where it falls outside them. */
  def clamp(min: Option[CalendarDate], max: Option[CalendarDate]): CalendarDate =
    if (min.exists(m => isBefore(m))) min.get else if (max.exists(m => isAfter(m))) max.get else this

  def sameMonth(other: CalendarDate): Boolean = year == other.year && month == other.month
  def firstOfMonth: CalendarDate = if (day == 1) this else CalendarDate(year, month, 1)
  def lastOfMonth: CalendarDate = CalendarDate(year, month, daysInMonth)
  /** The first day of its week, a week starting on `firstDay` (1 Monday to 7 Sunday: `everyui.i18n.WeekStart`). */
  def startOfWeek(firstDay: Int): CalendarDate = plusDays(-CalendarDate.floorMod(weekday - firstDay, 7))
  def endOfWeek(firstDay: Int): CalendarDate = startOfWeek(firstDay).plusDays(6)

  /** Midnight at its start in UTC, in milliseconds since the epoch. */
  def toEpochMillis: Long = epochDay.toLong * CalendarDate.DayMillis

  /** Midnight at its start where the device is (`Platform.localDateTime`), in milliseconds since the epoch. On a day
    * whose midnight a change of the clocks skips, the first moment it has. */
  def toLocalEpochMillis: Long = {
    val utc = toEpochMillis
    val guess = utc - CalendarDate.localOffsetMillis(utc)
    val moment = utc - CalendarDate.localOffsetMillis(guess)
    if (CalendarDate.fromLocalEpochMillis(moment) == this) moment else guess
  }

  /** The ISO 8601 form, `2026-09-29` (a year past 9999 or before 0 with its sign, as ISO's expanded form). */
  def iso: String = {
    val y = math.abs(year)
    val digits = if (y < 10) "000" + y.toString else if (y < 100) "00" + y.toString else if (y < 1000) "0" + y.toString
      else y.toString
    (if (year < 0) "-" else if (year > 9999) "+" else "") + digits + "-" + CalendarDate.two(month) + "-" +
      CalendarDate.two(day)
  }

  override def toString: String = iso
}

object CalendarDate {
  val DayMillis: Long = 86400000L

  /** The day `epochDay` days after 1970-01-01. */
  def ofEpochDay(epochDay: Int): CalendarDate = {
    // Howard Hinnant's civil_from_days: 400-year eras of 146097 days, the year counted from March
    val z = epochDay + 719468
    val era = floorDiv(z, 146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    CalendarDate(yoe + era * 400 + (if (m <= 2) 1 else 0), m, d)
  }

  /** Whether `year`, month and day are a day on the calendar. */
  def valid(year: Int, month: Int, day: Int): Boolean =
    month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth(year, month)

  /** The day, where it is one: February 30 is None. */
  def of(year: Int, month: Int, day: Int): Option[CalendarDate] =
    if (valid(year, month, day)) Some(CalendarDate(year, month, day)) else None

  def leapYear(year: Int): Boolean = floorMod(year, 4) == 0 && (floorMod(year, 100) != 0 || floorMod(year, 400) == 0)

  def daysInMonth(year: Int, month: Int): Int =
    if (month == 2) (if (leapYear(year)) 29 else 28)
    else if (month == 4 || month == 6 || month == 9 || month == 11) 30
    else 31

  /** An ISO 8601 date, `2026-09-29` (or `+12026-09-29`, `-0044-03-15`), where it is a day on the calendar. */
  def parse(text: String): Option[CalendarDate] = {
    val t = text.trim
    val sign = if (t.startsWith("-")) -1 else 1
    val body = if (t.startsWith("-") || t.startsWith("+")) t.substring(1) else t
    val parts = body.split("-").toList
    if (parts.length != 3 || parts(0).length < 4 || parts(1).length != 2 || parts(2).length != 2 ||
      !parts.forall(p => p.nonEmpty && p.forall(c => c >= '0' && c <= '9'))) None
    else of(sign * parts(0).toInt, parts(1).toInt, parts(2).toInt)
  }

  /** The UTC day `millis` since the epoch falls on. */
  def fromEpochMillis(millis: Long): CalendarDate = ofEpochDay(floorDivLong(millis, DayMillis).toInt)

  /** The day `millis` since the epoch falls on where the device is. */
  def fromLocalEpochMillis(millis: Long): CalendarDate = {
    val local = Platform.localDateTime(millis)
    parse(local.substring(0, 10)).getOrElse(fromEpochMillis(millis))
  }

  /** How far the device's clock is ahead of UTC at `millis`, in milliseconds (negative west of Greenwich), as the
    * platform tells it: its local date and time at that moment, read as though it were UTC, less the moment. */
  def localOffsetMillis(millis: Long): Long = {
    val local = Platform.localDateTime(millis)
    parse(local.substring(0, 10)) match {
      case Some(date) if local.length >= 19 =>
        val clock = local.substring(11, 13).toInt * 3600000L + local.substring(14, 16).toInt * 60000L +
          local.substring(17, 19).toInt * 1000L
        // to the second the platform gives, so the moment's own milliseconds are no part of the offset
        date.toEpochMillis + clock - (millis - floorModLong(millis, 1000L))
      case _ => 0L
    }
  }

  /** The day it is now where the device is. */
  def today(): CalendarDate = fromLocalEpochMillis(System.currentTimeMillis())

  /** The earlier of the two. */
  def min(a: CalendarDate, b: CalendarDate): CalendarDate = if (b.isBefore(a)) b else a
  /** The later of the two. */
  def max(a: CalendarDate, b: CalendarDate): CalendarDate = if (b.isAfter(a)) b else a

  /** Days from 1970-01-01 to `year`-`month`-`day` (Hinnant's days_from_civil). */
  private[date] def daysFromCivil(year: Int, month: Int, day: Int): Int = {
    val y = if (month <= 2) year - 1 else year
    val era = floorDiv(y, 400)
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    era * 146097 + doe - 719468
  }

  private[date] def floorDiv(a: Int, b: Int): Int = {
    val q = a / b
    if ((a % b != 0) && ((a < 0) != (b < 0))) q - 1 else q
  }

  private[date] def floorMod(a: Int, b: Int): Int = a - floorDiv(a, b) * b

  private[date] def floorDivLong(a: Long, b: Long): Long = {
    val q = a / b
    if ((a % b != 0L) && ((a < 0L) != (b < 0L))) q - 1L else q
  }

  private[date] def floorModLong(a: Long, b: Long): Long = a - floorDivLong(a, b) * b

  private[date] def two(n: Int): String = if (n < 10) "0" + n.toString else n.toString
}
