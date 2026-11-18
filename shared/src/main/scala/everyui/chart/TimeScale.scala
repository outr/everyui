package everyui.chart

import everyui.date.CalendarDate
import everyui.i18n.{DateText, WeekStart}

/** Moments from `low` to `high` (epoch milliseconds) laid along pixels from `start` to `end`: a time axis. Its ticks
  * fall on whole units of the clock and the calendar where the reader is (seconds, minutes, hours, days, weeks,
  * months, years), as many as fit about `count`, each written as that unit is: "3:00 PM", "Nov 18", "Mar", "2026".
  *
  * {{{
  *   val x = TimeScale(firstDay, lastDay, 0.0, 600.0)
  *   x.ticks(6)   // a tick a day, or every week, as the span asks
  * }}} */
case class TimeScale(low: Long, high: Long, start: Double = 0.0, end: Double = 1.0) {
  /** Where moment `t` falls. */
  def apply(t: Double): Double = {
    val span = (high - low).toDouble
    if (span == 0.0) start else start + (t - low.toDouble) / span * (end - start)
  }

  def placed(start: Double, end: Double): TimeScale = copy(start = start, end = end)

  /** The unit ticks fall on for about `count` of them, as (kind, how many): ("h", 6) is every six hours. */
  def unit(count: Int): (String, Int) = {
    val span = math.max(1L, high - low).toDouble
    TimeScale.units.find(u => span / TimeScale.millis(u) <= math.max(1, count)).getOrElse {
      val years = span / TimeScale.millis(("y", 1)) / math.max(1, count)
      ("y", math.max(1.0, LinearScale.niceNumber(years, round = false)).toInt)
    }
  }

  /** The ticks between `low` and `high`, about `count` of them, at whole units of local time (`offset` says how far
    * ahead of UTC the reader's clock is at a moment: the device's own by default). */
  def ticks(count: Int = 6, offset: Long => Long = CalendarDate.localOffsetMillis): Vector[Tick] = {
    val (kind, n) = unit(count)
    val moments = kind match {
      case "s" | "m" | "h" =>
        // a whole number of these on the reader's clock: midnight, the hour, the quarter hour
        val size = TimeScale.millis((kind, n)).toLong
        val off = offset(low)
        var at = TimeScale.ceilDiv(low + off, size) * size
        var out = Vector.empty[Long]
        while (at <= high + off) {
          out = out :+ (at - off)
          at += size
        }
        out
      case "d" | "w" =>
        val days = if (kind == "w") 7 * n else n
        val day0 = local(low, offset)
        val begin = if (kind == "w") day0.startOfWeek(WeekStart.current()) else day0
        dates(begin, d => d.plusDays(days), offset)
      case "mo" =>
        val day0 = local(low, offset)
        val begin = CalendarDate(day0.year, day0.month - (day0.month - 1) % n, 1)
        dates(begin, d => d.plusMonths(n), offset)
      case _ =>
        val day0 = local(low, offset)
        dates(CalendarDate(day0.year - TimeScale.floorMod(day0.year, n), 1, 1), d => d.plusYears(n), offset)
    }
    moments.map(t => Tick(t.toDouble, TimeScale.label(kind, t + offset(t))))
  }

  // the days from `begin` on, each at the reader's midnight, within the span
  private def dates(begin: CalendarDate, next: CalendarDate => CalendarDate, offset: Long => Long): Vector[Long] = {
    def at(d: CalendarDate): Long = {
      val utc = d.toEpochMillis
      utc - offset(utc)
    }
    var d = begin
    while (at(d) < low) d = next(d)
    var out = Vector.empty[Long]
    while (at(d) <= high) {
      out = out :+ at(d)
      d = next(d)
    }
    out
  }

  private def local(t: Long, offset: Long => Long): CalendarDate =
    TimeScale.dayOf(t + offset(t))
}

object TimeScale {
  val Second: Long = 1000L
  val Minute: Long = 60000L
  val Hour: Long = 3600000L
  val Day: Long = 86400000L

  /** The units ticks may fall on, shortest first. */
  val units: Vector[(String, Int)] = Vector(("s", 1), ("s", 5), ("s", 15), ("s", 30), ("m", 1), ("m", 5), ("m", 15),
    ("m", 30), ("h", 1), ("h", 3), ("h", 6), ("h", 12), ("d", 1), ("d", 2), ("w", 1), ("w", 2), ("mo", 1), ("mo", 3),
    ("mo", 6), ("y", 1))

  /** About how long `unit` is (a month 30 days, a year 365). */
  def millis(unit: (String, Int)): Double = {
    val one = unit._1 match {
      case "s" => Second
      case "m" => Minute
      case "h" => Hour
      case "d" => Day
      case "w" => 7L * Day
      case "mo" => 30L * Day
      case _ => 365L * Day
    }
    one.toDouble * unit._2
  }

  /** A tick of `kind` at `local` (milliseconds on the reader's clock, read as though UTC) in words: the time for a
    * part of a day (the day itself at midnight), the day for days and weeks, the month (with its year in January),
    * the year. */
  def label(kind: String, local: Long): String = {
    val date = TimeScale.dayOf(local)
    val inDay = local - floorDiv(local, Day) * Day
    val hour = (inDay / Hour).toInt
    val minute = ((inDay % Hour) / Minute).toInt
    kind match {
      case "s" => DateText.timeWithSeconds(hour, minute, ((inDay % Minute) / Second).toInt)
      case "m" | "h" => if (inDay == 0L) DateText.monthDay(date.month, date.day) else DateText.timeOfDay(hour, minute)
      case "d" | "w" => DateText.monthDay(date.month, date.day)
      case "mo" => if (date.month == 1) DateText.monthYear(date.year, 1) else DateText.monthName(date.month, short = true)
      case _ => date.year.toString
    }
  }

  /** The day `local` (milliseconds on the reader's clock, read as though UTC) falls on. (Its number is worked out as a
    * Double and made whole from that: a Roku keeps a Long made an Int a LongInteger, which no list is indexed by.) */
  def dayOf(local: Long): CalendarDate = CalendarDate.ofEpochDay(math.floor(local.toDouble / Day.toDouble).toInt)

  def floorDiv(a: Long, b: Long): Long = {
    val q = a / b
    if ((a % b != 0L) && ((a < 0L) != (b < 0L))) q - 1L else q
  }
  def ceilDiv(a: Long, b: Long): Long = -floorDiv(-a, b)
  def floorMod(a: Int, b: Int): Int = a - (floorDiv(a.toLong, b.toLong) * b).toInt
}
