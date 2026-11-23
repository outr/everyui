package everyui.date

import everyui.render.Platform

/** The device's clock at a moment, without a formatter or a zone lookup each time: what `CalendarDate`'s local days,
  * `ClockTime.now`, a transcript's times and a log's are worked out from.
  *
  * The platform is asked for its offset from UTC (`Platform.zoneOffset`) once for each six hours of moments: at the
  * span's start and its end, and where it knows its zone's rules (the JVM, Android 8 on) for its next change of the
  * clocks. Within a span a moment's local time is plain arithmetic; a span the clocks change in twice, or change in
  * where the platform cannot say when, is asked at each moment. The last two spans are kept (now's, and a message's),
  * and forgotten as the zone changes: `reset` (`TimeZones.check` and Android's time-zone broadcast call it), else as
  * `Platform.timeZone` is seen to have changed, read again at most every two seconds.
  *
  * The answers are the platform's own (`Platform.localDateTime`'s), from 1900 to 9999; outside those years it is asked
  * each time.
  *
  * {{{
  *   LocalClock.dateTime(message.time)          // "2026-11-22 15:07:42"
  *   LocalClock.date(message.time)              // CalendarDate(2026, 11, 22)
  *   LocalClock.secondOfDay(message.time)       // 54462: 15:07:42
  *   LocalClock.offset(message.time)            // -21600000 in Chicago in winter
  * }}} */
object LocalClock {
  private val SpanMillis: Long = 6L * 3600000L
  private val First: Long = CalendarDate(1900, 1, 2).toEpochMillis
  private val Last: Long = CalendarDate(9999, 12, 30).toEpochMillis

  /** How often, at most, the zone is read again to see whether it changed, in milliseconds. */
  private[everyui] var checkEvery: Long = 2000L

  // no span: none has its index
  private val Unknown = LocalSpan(Long.MinValue, 0L, 0L, 0L, exact = true)

  @volatile private var zone: String = ""
  @volatile private var nextCheck: Long = Long.MinValue
  @volatile private var recent: LocalSpan = Unknown
  @volatile private var other: LocalSpan = Unknown

  /** How far the device's clock is ahead of UTC at `millis`, in milliseconds (negative west of Greenwich). */
  def offset(millis: Long): Long =
    if (millis < First || millis >= Last) CalendarDate.platformOffsetMillis(millis)
    else {
      val s = span(CalendarDate.floorDivLong(millis, SpanMillis))
      if (s.exact) Platform.zoneOffset(millis) else s.offset(millis)
    }

  /** `millis` since the epoch as the device's local `yyyy-MM-dd HH:mm:ss`, as `Platform.localDateTime` has it. */
  def dateTime(millis: Long): String =
    if (millis < First || millis >= Last) Platform.localDateTime(millis)
    else {
      val local = millis + offset(millis)
      val seconds = (CalendarDate.floorModLong(local, CalendarDate.DayMillis) / 1000L).toInt
      CalendarDate.fromEpochMillis(local).iso + " " + CalendarDate.two(seconds / 3600) + ":" +
        CalendarDate.two(seconds / 60 % 60) + ":" + CalendarDate.two(seconds % 60)
    }

  /** The seconds since midnight on the device's clock at `millis`, 0 to 86399. */
  def secondOfDay(millis: Long): Int =
    if (millis < First || millis >= Last) {
      val local = Platform.localDateTime(millis)
      local.substring(11, 13).toInt * 3600 + local.substring(14, 16).toInt * 60 + local.substring(17, 19).toInt
    }
    else (CalendarDate.floorModLong(millis + offset(millis), CalendarDate.DayMillis) / 1000L).toInt

  /** The day `millis` since the epoch falls on where the device is. */
  def date(millis: Long): CalendarDate =
    if (millis < First || millis >= Last) CalendarDate.platformDate(millis)
    else CalendarDate.fromEpochMillis(millis + offset(millis))

  /** The spans forgotten: the zone changed. */
  def reset(): Unit = {
    nextCheck = Long.MinValue
    other = Unknown
    recent = Unknown
  }

  private def span(index: Long): LocalSpan = {
    checkZone()
    val r = recent
    if (r.index == index) r
    else {
      val o = other
      if (o.index == index) {
        other = r
        recent = o
        o
      }
      else {
        val made = measure(index)
        other = r
        recent = made
        made
      }
    }
  }

  private def checkZone(): Unit = {
    val now = System.currentTimeMillis()
    if (now >= nextCheck || now < nextCheck - checkEvery) {
      nextCheck = now + checkEvery
      val z = Platform.timeZone()
      if (z != zone) {
        zone = z
        other = Unknown
        recent = Unknown
      }
    }
  }

  // the span asked of the platform: its offset at each end, and where the platform knows its rules, its changes
  private def measure(index: Long): LocalSpan = {
    val start = index * SpanMillis
    val end = start + SpanMillis
    val first = Platform.zoneOffset(start)
    val last = Platform.zoneOffset(end - 1L)
    val change = Platform.zoneChangeAfter(start)
    if (change <= start || change >= end) LocalSpan(index, first, end, first, exact = first != last)
    else if (Platform.zoneChangeAfter(change) >= end && Platform.zoneOffset(change) == last)
      LocalSpan(index, first, change, last, exact = false)
    else LocalSpan(index, first, end, first, exact = true)
  }
}
