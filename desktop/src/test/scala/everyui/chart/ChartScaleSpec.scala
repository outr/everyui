package everyui.chart

import everyui.i18n.WeekStart
import everyui.spatial.Rect
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The maths under the charts: round ticks, time ticks on whole units, category labels kept apart, bars side by side
  * and stacked, slices and their hits, folding into "Other", and the words a value is written in. */
class ChartScaleSpec extends AnyWordSpec with Matchers {
  private val day = TimeScale.Day
  private val utc: Long => Long = _ => 0L
  // 2026-11-01, a Sunday, at midnight UTC
  private val nov1 = everyui.date.CalendarDate(2026, 11, 1).toEpochMillis

  "Nice ticks" should {
    "widen to steps of 1, 2 or 5 times a power of ten, the ends on whole steps" in {
      val s = LinearScale.nice(0.0, 87.0, 5)
      (s.low, s.high, s.step) shouldBe (0.0, 100.0, 20.0)
      s.ticks shouldBe Vector(0.0, 20.0, 40.0, 60.0, 80.0, 100.0)
      LinearScale.nice(-13.0, 42.0, 5).ticks shouldBe Vector(-20.0, 0.0, 20.0, 40.0, 60.0)
      LinearScale.nice(0.12, 0.94, 5).ticks shouldBe Vector(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
      LinearScale.nice(1200.0, 48700.0, 6).step shouldBe 10000.0
    }
    "write decimals without floating point's dust, and nothing never as -0" in {
      LinearScale(0.0, 0.3, 0.1).ticks shouldBe Vector(0.0, 0.1, 0.2, 0.3)
      LinearScale.nice(0.0, 0.3, 4).ticks shouldBe Vector(0.0, 0.2, 0.4)
      LinearScale(-0.3, 0.0, 0.1).ticks.map(_.toString) shouldBe Vector("-0.3", "-0.2", "-0.1", "0.0")
      LinearScale.clean(-1e-17, 0.1) shouldBe 0.0
      1.0 / LinearScale.clean(-1e-17, 0.1) shouldBe Double.PositiveInfinity
    }
    "widen a span of nothing round its value" in {
      (LinearScale.nice(0.0, 0.0).low, LinearScale.nice(0.0, 0.0).high) shouldBe (0.0, 1.0)
      (LinearScale.nice(5.0, 5.0).low, LinearScale.nice(5.0, 5.0).high) shouldBe (0.0, 5.0)
      (LinearScale.nice(-4.0, -4.0).low, LinearScale.nice(-4.0, -4.0).high) shouldBe (-4.0, 0.0)
    }
    "place values along pixels running either way" in {
      val y = LinearScale.nice(0.0, 87.0, 5).placed(200.0, 0.0)
      y(0.0) shouldBe 200.0
      y(50.0) shouldBe 100.0
      y.invert(50.0) shouldBe 75.0
    }
  }

  "Time ticks" should {
    "fall on the reader's midnights a week apart over a month, from the week's first day" in {
      WeekStart.chosen @= Some(7)
      try {
        val t = TimeScale(nov1 - 6L * day + 3600000L, nov1 + 23L * day)
        t.unit(6) shouldBe ("w", 1)
        val ticks = t.ticks(6, utc)
        ticks.map(_.value.toLong) shouldBe Vector(nov1, nov1 + 7L * day, nov1 + 14L * day, nov1 + 21L * day)
        ticks.map(_.label) shouldBe Vector("Nov 1", "Nov 8", "Nov 15", "Nov 22")
      }
      finally WeekStart.chosen @= None
    }
    "fall on whole hours over a day, the day itself at midnight" in {
      val t = TimeScale(nov1, nov1 + day)
      t.unit(6) shouldBe ("h", 6)
      t.ticks(6, utc).map(_.label) shouldBe Vector("Nov 1", "6:00 AM", "12:00 PM", "6:00 PM", "Nov 2")
    }
    "fall on the first of every third month over a year, January with its year" in {
      val start = everyui.date.CalendarDate(2026, 1, 1).toEpochMillis
      val t = TimeScale(start + day, start + 364L * day)
      t.unit(6) shouldBe ("mo", 3)
      t.ticks(6, utc).map(_.label) shouldBe Vector("Apr", "Jul", "Oct")
      TimeScale(start, start + 364L * day).ticks(6, utc).head.label should include("2026")
    }
    "follow the reader's clock" in {
      // five hours behind UTC: the ticks at the reader's midnight and six, not UTC's
      val behind: Long => Long = _ => -5L * 3600000L
      val ticks = TimeScale(nov1, nov1 + day).ticks(6, behind)
      ticks.head.value.toLong shouldBe nov1 + 5L * 3600000L - 6L * 3600000L + 6L * 3600000L
      ticks.head.label shouldBe "Nov 1"
    }
  }

  "Category labels" should {
    "stay level where they fit, turn where they fit turned, and thin out where neither" in {
      LabelPlan.of(Vector(30.0, 28.0), 14.0, 40.0, 100.0) shouldBe LabelPlan(1, rotated = false)
      LabelPlan.of(Vector(30.0, 28.0), 14.0, 25.0, 100.0) shouldBe LabelPlan(1, rotated = true)
      LabelPlan.of(Vector(30.0, 28.0), 14.0, 15.0, 100.0) shouldBe LabelPlan(3, rotated = false)
      // turned, they would reach further down than there is room for
      LabelPlan.of(Vector(300.0), 14.0, 25.0, 100.0) shouldBe LabelPlan(13, rotated = false)
      LabelPlan(3, rotated = false).shows(0) shouldBe true
      (0 until 7).filter(LabelPlan(3, rotated = false).shows) shouldBe Seq(0, 3, 6)
    }
    "write whole ticks without a fraction of nothing" in {
      AxisChart.whole("$100.00") shouldBe "$100"
      AxisChart.whole("12.0K") shouldBe "12K"
      AxisChart.whole("1.5 h") shouldBe "1.5 h"
      AxisChart.whole("1,000") shouldBe "1,000"
      AxisChart.whole("2.50") shouldBe "2.50"
    }
  }

  "Bars" should {
    val scale = LinearScale.nice(0.0, 6.0, 4).placed(100.0, 0.0)
    val band = BandScale(2, 0.0, 200.0)
    "stack each category's values, each segment's foot a gap above the one under it, the last rounded" in {
      val bars = BarChart.bars(Vector(Vector(1.0, 2.0), Vector(3.0, 4.0)), scale, band, stacked = true,
        horizontal = false)
      bars.size shouldBe 4
      def at(c: Int, s: Int) = bars.find(b => b._1.category == c && b._1.series == s).get
      // the top of each stack is its sum
      at(0, 1)._1.y shouldBe scale(4.0) +- 1e-9
      at(1, 1)._1.y shouldBe scale(6.0) +- 1e-9
      // the first segment stands on the baseline; the second stops a gap short of the first
      (at(0, 0)._1.y + at(0, 0)._1.height) shouldBe scale(0.0) +- 1e-9
      (at(0, 1)._1.y + at(0, 1)._1.height) shouldBe (scale(1.0) - BarChart.gap) +- 1e-9
      at(0, 0)._2 shouldBe ""
      at(0, 1)._2 shouldBe "top"
      BarChart.extent(Vector(Vector(1.0, -2.0), Vector(3.0, -4.0)), stacked = true) shouldBe (-6.0, 4.0)
      BarChart.extent(Vector(Vector(1.0, -2.0), Vector(3.0, -4.0)), stacked = false) shouldBe (-4.0, 3.0)
    }
    "stand side by side, at most 24 thick and 2 apart, below nothing rounded at the bottom" in {
      val bars = BarChart.bars(Vector(Vector(1.0, -2.0), Vector(3.0, 4.0)), LinearScale.nice(-2.0, 4.0, 4)
        .placed(100.0, 0.0), band, stacked = false, horizontal = false)
      val (a, b) = (bars(0)._1, bars(1)._1)
      a.width shouldBe 24.0
      (b.x - (a.x + a.width)) shouldBe BarChart.gap
      bars.find(x => x._1.category == 1 && x._1.series == 0).get._2 shouldBe "bottom"
      val sideways = BarChart.bars(Vector(Vector(2.0)), LinearScale.nice(0.0, 4.0, 3).placed(0.0, 100.0),
        BandScale(1, 0.0, 40.0), stacked = false, horizontal = true)
      sideways.head._1 shouldBe ChartMark(0, 0, 2.0, 0.0, 8.0, 50.0, 24.0)
      sideways.head._2 shouldBe "right"
    }
    "be found by a box at least 24 across round each, the nearest where they overlap" in {
      val small = ChartMark.around(0, 0, 1.0, 50.0, 90.0, 4.0, 4.0)
      (small.width, small.height) shouldBe (24.0, 24.0)
      val marks = Vector(ChartMark.around(0, 0, 1.0, 10.0, 50.0, 10.0, 50.0), ChartMark.around(0, 1, 2.0, 22.0, 50.0, 10.0, 50.0))
      ChartMark.at(marks, 14.0, 60.0).map(_.series) shouldBe Some(0)
      ChartMark.at(marks, 27.0, 60.0).map(_.series) shouldBe Some(1)
      ChartMark.at(marks, 80.0, 60.0) shouldBe None
    }
  }

  "Slices" should {
    val quarters = Vector((0.0, math.Pi / 2.0), (math.Pi / 2.0, math.Pi), (math.Pi, 2.0 * math.Pi))
    "be found by the angle from twelve o'clock, within the ring" in {
      DonutChart.sliceAt(quarters, 50.0, 50.0, 30.0, 50.0, 85.0, 30.0) shouldBe Some(0)   // two o'clock
      DonutChart.sliceAt(quarters, 50.0, 50.0, 30.0, 50.0, 80.0, 80.0) shouldBe Some(1)   // half past four
      DonutChart.sliceAt(quarters, 50.0, 50.0, 30.0, 50.0, 10.0, 50.0) shouldBe Some(2)   // nine
      DonutChart.sliceAt(quarters, 50.0, 50.0, 30.0, 50.0, 55.0, 10.0) shouldBe Some(0)   // just past twelve
      DonutChart.sliceAt(quarters, 50.0, 50.0, 30.0, 50.0, 50.0, 50.0) shouldBe None      // in the hole
      DonutChart.sliceAt(quarters, 50.0, 50.0, 30.0, 50.0, 99.0, 99.0) shouldBe None      // outside
    }
    "fold the smallest past the most into Other, last" in {
      DonutChart.fold(Vector("a", "b", "c", "d"), Vector(5.0, 1.0, 3.0, 2.0), 3, "Other") shouldBe
        Vector(("a", 5.0), ("c", 3.0), ("Other", 3.0))
      DonutChart.fold(Vector("a", "b"), Vector(5.0, -1.0), 6, "Other") shouldBe Vector(("a", 5.0), ("b", 0.0))
    }
    "be laid out as blocks of their share where curves are not drawn" in {
      DonutChart.segments(Vector(1.0, 0.0, 3.0), Rect(0.0, 0.0, 102.0, 28.0)) shouldBe
        Vector(Rect(0.0, 0.0, 25.0, 28.0), Rect(27.0, 0.0, 0.0, 28.0), Rect(27.0, 0.0, 75.0, 28.0))
    }
  }

  "Values" should {
    "be written in full, short, as money and as shares" in {
      ChartFormat.number(1284.5) shouldBe "1,284.5"
      ChartFormat.number(-0.001) shouldBe "0"
      ChartFormat.compact(9999.0) shouldBe "9,999"
      ChartFormat.compact(12900.0) shouldBe "12.9K"
      ChartFormat.compact(4200000.0) shouldBe "4.2M"
      ChartFormat.money("$")(4.2) shouldBe "$4.20"
      ChartFormat.money("$")(-1284.5) shouldBe "-$1,284.50"
      ChartFormat.money("$")(48700.0) shouldBe "$48.7K"
      ChartFormat.percent(0.456) shouldBe "46%"
    }
  }
}
