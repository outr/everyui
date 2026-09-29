package everyui.date

import everyui.event.{Key, KeyEvent}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A range chosen a day at a time, how its days are drawn, the presets, and where the keys move a calendar's focus. */
class RangeChoiceSpec extends AnyWordSpec with Matchers {
  private def day(d: Int): CalendarDate = CalendarDate(2026, 9, d)

  "RangeChoice" should {
    "take a start, then an end" in {
      val started = RangeChoice().choose(day(3))
      started.waiting shouldBe true
      started.range shouldBe None
      val done = started.choose(day(9))
      done.range shouldBe Some(DateRange(day(3), day(9)))
      done.waiting shouldBe false
      started.choose(day(3)).range shouldBe Some(DateRange(day(3), day(3)))
    }
    "start again at a day before the start, or after a range is chosen" in {
      RangeChoice().choose(day(9)).choose(day(3)) shouldBe RangeChoice(Some(day(3)), None)
      RangeChoice(Some(day(3)), Some(day(9))).choose(day(20)) shouldBe RangeChoice(Some(day(20)), None)
    }
    "preview the range the day pointed at would make" in {
      val started = RangeChoice().choose(day(3))
      started.preview(Some(day(7))) shouldBe Some(DateRange(day(3), day(7)))
      started.preview(Some(day(1))) shouldBe None
      started.preview(None) shouldBe None
      RangeChoice(Some(day(3)), Some(day(9))).preview(Some(day(20))) shouldBe None
    }
    "mark the ends and the band between" in {
      val chosen = RangeChoice(Some(day(3)), Some(day(6)))
      chosen.mark(day(2), None) shouldBe DayMark.plain
      chosen.mark(day(3), None) shouldBe DayMark(selected = true, after = true)
      chosen.mark(day(4), None) shouldBe DayMark(before = true, after = true)
      chosen.mark(day(6), None) shouldBe DayMark(selected = true, before = true)
      chosen.mark(day(7), Some(day(10))) shouldBe DayMark.plain
      val single = RangeChoice(Some(day(3)), Some(day(3)))
      single.mark(day(3), None) shouldBe DayMark(selected = true)
      val started = RangeChoice(Some(day(3)), None)
      started.mark(day(3), Some(day(5))) shouldBe DayMark(selected = true, after = true, preview = true)
      started.mark(day(4), Some(day(5))) shouldBe DayMark(before = true, after = true, preview = true)
      started.mark(day(5), Some(day(5))) shouldBe DayMark(before = true, preview = true)
      started.mark(day(6), Some(day(5))) shouldBe DayMark.plain
      started.mark(day(3), None) shouldBe DayMark(selected = true)
    }
  }

  "DatePreset" should {
    "work its range out from today" in {
      val today = day(29)
      DatePreset.lastDays("Last 7 days", 7).range(today) shouldBe DateRange(day(23), day(29))
      DatePreset.today("Today").range(today) shouldBe DateRange(today, today)
      DatePreset.thisMonth("This month").range(today) shouldBe DateRange(day(1), today)
      DatePreset.lastMonth("Last month").range(today) shouldBe DateRange(CalendarDate(2026, 8, 1), CalendarDate(2026, 8, 31))
      DatePreset.lastMonth("Last month").range(CalendarDate(2026, 3, 31)) shouldBe
        DateRange(CalendarDate(2026, 2, 1), CalendarDate(2026, 2, 28))
      DatePreset.yearToDate("This year").range(today) shouldBe DateRange(CalendarDate(2026, 1, 1), today)
    }
  }

  "CalendarKeys" should {
    val from = day(29)
    def press(key: Key, name: String = "", shift: Boolean = false, rtl: Boolean = false, first: Int = 1): Option[CalendarDate] =
      CalendarKeys.target(from, KeyEvent(key, name, shift = shift), rtl, first)
    "move by a day, a week, a month and a year" in {
      press(Key.ArrowLeft) shouldBe Some(day(28))
      press(Key.ArrowRight) shouldBe Some(day(30))
      press(Key.ArrowUp) shouldBe Some(day(22))
      press(Key.ArrowDown) shouldBe Some(CalendarDate(2026, 10, 6))
      press(Key.PageUp) shouldBe Some(CalendarDate(2026, 8, 29))
      press(Key.PageDown) shouldBe Some(CalendarDate(2026, 10, 29))
      press(Key.PageUp, shift = true) shouldBe Some(CalendarDate(2025, 9, 29))
      press(Key.PageDown, shift = true) shouldBe Some(CalendarDate(2027, 9, 29))
      CalendarKeys.target(CalendarDate(2026, 1, 31), KeyEvent(Key.PageDown, "PageDown"), false, 1) shouldBe
        Some(CalendarDate(2026, 2, 28))
    }
    "mirror Left and Right in a right-to-left language" in {
      press(Key.ArrowLeft, rtl = true) shouldBe Some(day(30))
      press(Key.ArrowRight, rtl = true) shouldBe Some(day(28))
    }
    "go to the week's start and end, for its first day" in {
      press(Key.Other, "Home") shouldBe Some(day(28))
      press(Key.Other, "End") shouldBe Some(CalendarDate(2026, 10, 4))
      press(Key.Other, "Home", first = 7) shouldBe Some(day(27))
      press(Key.Other, "End", first = 7) shouldBe Some(CalendarDate(2026, 10, 3))
      press(Key.Other, "Home", first = 6) shouldBe Some(day(26))
    }
    "leave other keys" in {
      press(Key.Enter) shouldBe None
      press(Key.Other, "a") shouldBe None
    }
  }
}
