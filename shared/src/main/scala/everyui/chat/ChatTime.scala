package everyui.chat

import everyui.i18n.{DateText, Strings}
import everyui.render.Platform

/** Times as a transcript shows them, in the language in use: how long ago a message was ("now", "5m", then the time of
  * day), a day as its separator names it ("Today", "Yesterday", "Wed, Sep 16"), and how long something has taken
  * ("12s", "1m 9s"). The local calendar comes from the platform (`Platform.localDateTime`). */
object ChatTime {
  private val Minute = 60000L
  private val Hour = 3600000L
  private val Day = 86400000L

  /** When `time` was, seen at `now`: under a minute "now", under an hour "5m", else its time of day, and on another day
    * than `now`'s its date as well, unless `dated` says the date is shown elsewhere (a day's separator). */
  def relative(time: Long, now: Long, dated: Boolean = true): String = {
    val ago = now - time
    if (ago < Minute) ChatWords.now
    else if (ago < Hour) ChatWords.minutesAgo(ago / Minute)
    else if (!dated || dayOf(time) == dayOf(now)) clock(time)
    else {
      val (_, month, day) = parts(dayOf(time))
      DateText.monthDay(month, day) + " " + clock(time)
    }
  }

  /** How long ago `time` was, seen at `now`, as a list of conversations says it beside each one: under a minute "now",
    * then "5m", "3h", and "2d" within the week, and after that its date in numbers, the month and the day in the order
    * the language writes them ("9/16", "16/9"), with the year after them from another year ("9/16/25"). A time after
    * `now` (a clock that runs ahead) is "now". */
  def ago(time: Long, now: Long): String = {
    val since = now - time
    if (since < Minute) ChatWords.now
    else if (since < Hour) ChatWords.minutesAgo(since / Minute)
    else if (since < Day) ChatWords.hoursAgo(since / Hour)
    else if (since < Day * 7L) ChatWords.daysAgo(since / Day)
    else {
      val day = dayOf(time)
      val (year, month, date) = parts(day)
      val pattern = DateText.symbols(Strings.language().code).monthDay
      val dayFirst = pattern.indexOf('d') >= 0 && pattern.indexOf('d') < pattern.indexOf('M')
      val numbers = if (dayFirst) date.toString + "/" + month.toString else month.toString + "/" + date.toString
      if (day.substring(0, 4) == dayOf(now).substring(0, 4)) numbers
      else numbers + "/" + (if (year % 100 < 10) "0" else "") + (year % 100).toString
    }
  }

  /** The time of day `time` was, as the language writes it: "3:07 PM", "15:07". */
  def clock(time: Long): String = {
    val local = Platform.localDateTime(time)
    DateText.timeOfDay(number(local, 11, 13), number(local, 14, 16))
  }

  /** The local day `time` falls on, as "yyyy-MM-dd": what tells one day's entries from the next. */
  def dayOf(time: Long): String = Platform.localDateTime(time).substring(0, 10)

  /** A day ("yyyy-MM-dd") named for its separator, seen at `now`: "Today", "Yesterday", within the year "Wed, Sep 16",
    * else "Sep 16, 2025". */
  def dayName(day: String, now: Long): String =
    if (day == dayOf(now)) ChatWords.today
    else if (day == dayOf(now - Day)) ChatWords.yesterday
    else {
      val (year, month, date) = parts(day)
      if (day.substring(0, 4) == dayOf(now).substring(0, 4)) DateText.weekdayMonthDay(month, date, weekday(year, month, date))
      else DateText.mediumDate(year, month, date)
    }

  /** A span of `millis` as "12s", "1m 9s" or "2h 5m". */
  def span(millis: Long): String = {
    val seconds = math.max(0L, millis) / 1000L
    if (seconds < 60L) seconds.toString + "s"
    else if (seconds < 3600L) (seconds / 60L).toString + "m " + (seconds % 60L).toString + "s"
    else (seconds / 3600L).toString + "h " + ((seconds % 3600L) / 60L).toString + "m"
  }

  /** The weekday of a date, 1 (Monday) to 7, by its days from 1970-01-01 (a Thursday). */
  def weekday(year: Int, month: Int, day: Int): Int = {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146097 + doe - 719468
    ((days % 7 + 7 + 3) % 7) + 1
  }

  private def parts(day: String): (Int, Int, Int) = (number(day, 0, 4), number(day, 5, 7), number(day, 8, 10))

  private def number(s: String, from: Int, to: Int): Int = s.substring(from, to).toInt
}
