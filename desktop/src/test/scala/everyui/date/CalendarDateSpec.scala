package everyui.date

import java.time.LocalDate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The date arithmetic, checked against java.time (which the shared code cannot use, since a Roku has none): every day
  * from 1600 to 2400 and a span round year 0, leap years, month ends, weekdays across centuries, ISO 8601 both ways,
  * and the conversions to and from the epoch. */
class CalendarDateSpec extends AnyWordSpec with Matchers {
  private def of(d: LocalDate): CalendarDate = CalendarDate(d.getYear, d.getMonthValue, d.getDayOfMonth)

  "CalendarDate" should {
    "number every day as java.time does, with its weekday, from 1600 to 2400" in {
      var d = LocalDate.of(1600, 1, 1)
      val stop = LocalDate.of(2400, 12, 31)
      while (!d.isAfter(stop)) {
        val c = of(d)
        c.epochDay shouldBe d.toEpochDay.toInt
        c.weekday shouldBe d.getDayOfWeek.getValue
        CalendarDate.ofEpochDay(d.toEpochDay.toInt) shouldBe c
        c.dayOfYear shouldBe d.getDayOfYear
        d = d.plusDays(1)
      }
    }
    "count days round year 0 and before it" in {
      var d = LocalDate.of(-401, 1, 1)
      while (d.getYear < 402) {
        val c = of(d)
        c.epochDay shouldBe d.toEpochDay.toInt
        CalendarDate.ofEpochDay(c.epochDay) shouldBe c
        c.weekday shouldBe d.getDayOfWeek.getValue
        d = d.plusDays(1)
      }
    }
    "know the leap years" in {
      List(1600, 2000, 2004, 2024, 2400, 0, -4).foreach(y => CalendarDate.leapYear(y) shouldBe true)
      List(1700, 1800, 1900, 2100, 2023, 2026, -1, -100).foreach(y => CalendarDate.leapYear(y) shouldBe false)
      (-500 to 3000).foreach(y => CalendarDate.leapYear(y) shouldBe java.time.Year.isLeap(y.toLong))
      CalendarDate.daysInMonth(2024, 2) shouldBe 29
      CalendarDate.daysInMonth(1900, 2) shouldBe 28
      CalendarDate.daysInMonth(2000, 2) shouldBe 29
      (1 to 12).foreach(m => CalendarDate.daysInMonth(2026, m) shouldBe java.time.YearMonth.of(2026, m).lengthOfMonth)
    }
    "add months and years as java.time does, keeping to the month's end" in {
      CalendarDate(2026, 1, 31).plusMonths(1) shouldBe CalendarDate(2026, 2, 28)
      CalendarDate(2024, 1, 31).plusMonths(1) shouldBe CalendarDate(2024, 2, 29)
      CalendarDate(2024, 2, 29).plusYears(1) shouldBe CalendarDate(2025, 2, 28)
      CalendarDate(2024, 2, 29).plusYears(4) shouldBe CalendarDate(2028, 2, 29)
      CalendarDate(2026, 3, 31).plusMonths(-1) shouldBe CalendarDate(2026, 2, 28)
      CalendarDate(2026, 1, 15).plusMonths(-13) shouldBe CalendarDate(2024, 12, 15)
      CalendarDate(1999, 12, 31).plusDays(1) shouldBe CalendarDate(2000, 1, 1)
      CalendarDate(2000, 3, 1).plusDays(-1) shouldBe CalendarDate(2000, 2, 29)
      CalendarDate(1900, 3, 1).plusDays(-1) shouldBe CalendarDate(1900, 2, 28)
      val rng = new scala.util.Random(7)
      (1 to 20000).foreach { _ =>
        val d = LocalDate.ofEpochDay(rng.nextInt(300000) - 150000L)
        val n = rng.nextInt(2000) - 1000
        of(d).plusMonths(n) shouldBe of(d.plusMonths(n.toLong))
        of(d).plusDays(n * 37) shouldBe of(d.plusDays(n * 37L))
        of(d).plusYears(n / 10) shouldBe of(d.plusYears(n / 10L))
      }
    }
    "give the weekday across centuries" in {
      CalendarDate(1970, 1, 1).weekday shouldBe 4      // a Thursday
      CalendarDate(2000, 1, 1).weekday shouldBe 6      // a Saturday
      CalendarDate(1900, 1, 1).weekday shouldBe 1      // a Monday
      CalendarDate(1776, 7, 4).weekday shouldBe 4
      CalendarDate(2100, 1, 1).weekday shouldBe 5
      CalendarDate(2026, 9, 29).weekday shouldBe 2     // a Tuesday
      CalendarDate(1582, 10, 15).weekday shouldBe 5    // Gregorian's first day, a Friday
    }
    "find a week's start and end for either first day" in {
      val tuesday = CalendarDate(2026, 9, 29)
      tuesday.startOfWeek(1) shouldBe CalendarDate(2026, 9, 28)
      tuesday.endOfWeek(1) shouldBe CalendarDate(2026, 10, 4)
      tuesday.startOfWeek(7) shouldBe CalendarDate(2026, 9, 27)
      tuesday.endOfWeek(7) shouldBe CalendarDate(2026, 10, 3)
      tuesday.startOfWeek(6) shouldBe CalendarDate(2026, 9, 26)
      CalendarDate(2026, 9, 27).startOfWeek(7) shouldBe CalendarDate(2026, 9, 27)
      CalendarDate(2026, 9, 27).startOfWeek(1) shouldBe CalendarDate(2026, 9, 21)
    }
    "compare, clamp and count" in {
      val a = CalendarDate(2026, 9, 1)
      val b = CalendarDate(2026, 9, 29)
      a.isBefore(b) shouldBe true
      b.isAfter(a) shouldBe true
      a.compare(a) shouldBe 0
      CalendarDate(2025, 12, 31).isBefore(CalendarDate(2026, 1, 1)) shouldBe true
      a.daysUntil(b) shouldBe 28
      CalendarDate(2024, 1, 1).daysUntil(CalendarDate(2025, 1, 1)) shouldBe 366
      CalendarDate(2026, 8, 1).clamp(Some(a), Some(b)) shouldBe a
      CalendarDate(2026, 10, 1).clamp(Some(a), Some(b)) shouldBe b
      CalendarDate(2026, 9, 9).within(Some(a), None) shouldBe true
      CalendarDate(2026, 8, 31).within(Some(a), None) shouldBe false
      CalendarDate.min(a, b) shouldBe a
      CalendarDate.max(a, b) shouldBe b
    }
    "write and read ISO 8601" in {
      CalendarDate(2026, 9, 29).iso shouldBe "2026-09-29"
      CalendarDate(2026, 9, 29).toString shouldBe "2026-09-29"
      CalendarDate(33, 1, 5).iso shouldBe "0033-01-05"
      CalendarDate(-44, 3, 15).iso shouldBe "-0044-03-15"
      CalendarDate(12026, 1, 1).iso shouldBe "+12026-01-01"
      CalendarDate.parse("2026-09-29") shouldBe Some(CalendarDate(2026, 9, 29))
      CalendarDate.parse(" 2024-02-29 ") shouldBe Some(CalendarDate(2024, 2, 29))
      CalendarDate.parse("-0044-03-15") shouldBe Some(CalendarDate(-44, 3, 15))
      CalendarDate.parse("+12026-01-01") shouldBe Some(CalendarDate(12026, 1, 1))
      List("2023-02-29", "2026-13-01", "2026-00-10", "2026-9-29", "26-09-29", "2026/09/29", "2026-09-29T10:00", "",
        "abcd-ef-gh", "2026-04-31").foreach(t => CalendarDate.parse(t) shouldBe None)
      var d = CalendarDate(1890, 1, 1)
      while (d.year < 2110) {
        CalendarDate.parse(d.iso) shouldBe Some(d)
        d.iso shouldBe LocalDate.of(d.year, d.month, d.day).toString
        d = d.plusDays(17)
      }
    }
    "turn into epoch milliseconds and back, in UTC and where the device is" in {
      CalendarDate(1970, 1, 1).toEpochMillis shouldBe 0L
      CalendarDate(2026, 9, 29).toEpochMillis shouldBe java.time.Instant.parse("2026-09-29T00:00:00Z").toEpochMilli
      CalendarDate.fromEpochMillis(-1L) shouldBe CalendarDate(1969, 12, 31)
      CalendarDate.fromEpochMillis(java.time.Instant.parse("2026-09-29T23:59:59.999Z").toEpochMilli) shouldBe
        CalendarDate(2026, 9, 29)
      val zone = java.time.ZoneId.systemDefault()
      val rng = new scala.util.Random(3)
      (1 to 500).foreach { _ =>
        val millis = 1500000000000L + (rng.nextLong() % 400000000000L)
        val local = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        CalendarDate.fromLocalEpochMillis(millis) shouldBe of(local.toLocalDate)
        CalendarDate.localOffsetMillis(millis) shouldBe local.getOffset.getTotalSeconds * 1000L
        val day = of(local.toLocalDate)
        day.toLocalEpochMillis shouldBe local.toLocalDate.atStartOfDay(zone).toInstant.toEpochMilli
      }
      CalendarDate.today() shouldBe of(LocalDate.now())
    }
  }

  "DateRange" should {
    "keep its ends in order, count its days and say what it holds" in {
      val r = DateRange.of(CalendarDate(2026, 9, 29), CalendarDate(2026, 9, 23))
      r shouldBe DateRange(CalendarDate(2026, 9, 23), CalendarDate(2026, 9, 29))
      r.days shouldBe 7
      r.contains(CalendarDate(2026, 9, 23)) shouldBe true
      r.contains(CalendarDate(2026, 9, 29)) shouldBe true
      r.contains(CalendarDate(2026, 9, 30)) shouldBe false
      r.single shouldBe false
      r.iso shouldBe "2026-09-23/2026-09-29"
      DateRange.parse("2026-09-23/2026-09-29") shouldBe Some(r)
      DateRange.parse("2026-09-29/2026-09-23") shouldBe None
      DateRange(CalendarDate(2026, 9, 29), CalendarDate(2026, 9, 23)).normalized shouldBe r
    }
  }
}
