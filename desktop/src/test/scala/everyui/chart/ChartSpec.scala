package everyui.chart

import everyui.component.Component
import everyui.event.{Key, KeyEvent}
import everyui.i18n.{Language, Strings}
import everyui.motion.ReduceMotion
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, ui}
import reactify.*

/** The charts as components: the marks a pointer, a press and the keys land on, told to the app; what a screen reader
  * hears and the data as a table; data that changes in place; the palette's series colours; and their words in every
  * language. Motion is reduced, so a change is drawn at once. */
class ChartSpec extends FxSpec {
  private val days = Vector("Mon", "Tue", "Wed")

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx(ReduceMotion.on @= true)
  }
  override def afterAll(): Unit = {
    fx(ReduceMotion.on @= false)
    super.afterAll()
  }

  /** `c` on the screen, `w` by `h`, for `f`, and taken away after. */
  private def shown[C <: Component, T](c: => C, w: Double = 400.0, h: Double = 240.0)(f: C => T): T = {
    val made = fx {
      val x = c
      x.size.width @= w
      x.size.height @= h
      ui.children += x
      x
    }
    try f(made)
    finally fx {
      ui.children -= made
      made.dispose()
    }
  }

  private def all(c: Component): List[Component] = c :: c.childComponents.toList.flatMap(all)

  "A bar chart" should {
    "tell the app which bar the pointer is on, and each press, and nothing where there is no bar" in {
      val values = Var(Vector(4.0, 9.0, 0.5))
      shown(BarChart(days, Vector(Series("Spend", values()))))(chart => {
        val heard = fx {
          var told = List.empty[Pointed]
          chart.pointed.attach(p => told = told :+ p)
          () => told
        }
        val (tue, _) = fx(chart.bars().find(_._1.category == 1).get)
        fx(chart.pointAt(tue.centerX, tue.centerY, pressed = false))
        fx(chart.hovered()) shouldBe Some(Pointed("Tue", "", 9.0, 1, 0))
        fx(chart.pointAt(tue.centerX, tue.y + 2.0, pressed = false))
        fx(heard()).size shouldBe 1
        fx(chart.pointAt(tue.centerX, tue.centerY, pressed = true))
        fx(heard()).last shouldBe Pointed("Tue", "", 9.0, 1, 0, pressed = true)
        // a short bar is heard 24 pixels round, above its top as well
        val (wed, _) = fx(chart.bars().find(_._1.category == 2).get)
        fx(chart.markAt(wed.centerX, wed.y - 5.0).map(_.category)) shouldBe Some(2)
        fx(chart.pointAt(1.0, 1.0, pressed = false))
        fx(chart.hovered()) shouldBe None
      })
    }
    "give each series of a category its own bar, and the tip every series' value there" in {
      shown(BarChart(days, Vector(Series("Work", Vector(1.0, 2.0, 3.0)), Series("Personal", Vector(4.0, 5.0, 6.0)))))(
        chart => {
          val personalTue = fx(chart.bars().find(b => b._1.category == 1 && b._1.series == 1).get._1)
          fx(chart.pointAt(personalTue.centerX, personalTue.centerY, pressed = false))
          fx(chart.hovered().map(p => (p.series, p.value))) shouldBe Some(("Personal", 5.0))
          // the legend names both, and the tip lists both
          fx(all(chart).collect { case l: Legend => l.entries().map(_.name) }) shouldBe List(Vector("Work", "Personal"))
          fx(all(chart).collect { case t: everyui.component.TextView => t.content() }) should contain allOf("2", "5")
        })
    }
    "stack its series, the top of each stack at its sum" in {
      shown(BarChart(days, Vector(Series("A", Vector(1.0, 2.0, 3.0)), Series("B", Vector(3.0, 4.0, 5.0))),
        stacked = true))(chart => fx {
        val s = chart.placedScale()
        days.indices.foreach { c =>
          val top = chart.bars().filter(_._1.category == c).map(_._1.y).min
          top shouldBe s(Vector(4.0, 6.0, 8.0)(c)) +- 1e-9
        }
      })
    }
    "move along its bars with the arrow keys, and let go with Escape" in {
      shown(BarChart(days, Vector(Series("A", Vector(1.0, 2.0, 3.0)), Series("B", Vector(3.0, 4.0, 5.0)))))(chart => {
        def press(k: Key): Unit = fx(chart.event.key.down @= KeyEvent(k, k.toString))
        press(Key.ArrowRight)
        fx(chart.hovered().map(p => (p.categoryIndex, p.seriesIndex))) shouldBe Some((0, 0))
        press(Key.ArrowRight)
        press(Key.ArrowDown)
        fx(chart.hovered().map(p => (p.category, p.series))) shouldBe Some(("Tue", "B"))
        fx(chart.keepsKey(Key.ArrowRight)) shouldBe true
        press(Key.Escape)
        fx(chart.hovered()) shouldBe None
      })
    }
    "follow its data in place, its table with it" in {
      val values = Var(Vector(4.0, 9.0, 2.0))
      shown(BarChart(days, Vector(Series("Spend", values()))))(chart => {
        def heights = fx(chart.bars().map(_._1.height))
        val before = heights
        fx(values @= Vector(8.0, 9.0, 2.0))
        heights(0) shouldBe (before(0) * 2.0) +- 0.5
        fx(chart.table @= true)
        def cells = fx(all(chart).collect { case v: ChartTableView => v }.flatMap(all)
          .collect { case t: everyui.component.TextView => t.content() })
        cells should contain inOrder("Category", "Spend", "Mon", "8")
        fx(values @= Vector(7.0, 9.0, 2.0))
        cells should contain("7")
        cells should not contain "8"
        // the drawing and its legend give way to the table, and come back
        fx(chart.plot.visible()) shouldBe false
        fx(chart.table @= false)
        fx(chart.plot.visible()) shouldBe true
      })
    }
    "say what it shows to a screen reader, or what the app says it shows" in {
      shown(BarChart(days, Vector(Series("Spend", Vector(4.0, 9.0, 2.0))), ChartFormat.money("$", 0)))(chart => {
        fx(chart.semantics().role) shouldBe "figure"
        fx(chart.semantics().label) shouldBe "Bar chart: Spend. 3 values, from $2 to $9; highest Tue, $9."
        fx(chart.summary @= "Spend peaked on Tuesday")
        fx(chart.semantics().label) shouldBe "Spend peaked on Tuesday"
        ChartTable.of("Category", days, Vector(Series("Spend", Vector(4.0, 9.0))), ChartFormat.number(_)) shouldBe
          ChartTable(Vector("Category", "Spend"), Vector(Vector("Mon", "4"), Vector("Tue", "9"), Vector("Wed", "0")))
      })
    }
  }

  "A line chart" should {
    "find the nearest category across, and the series whose line is nearest there" in {
      shown(LineChart(days, Vector(Series("High", Vector(10.0, 10.0, 10.0)), Series("Low", Vector(1.0, 1.0, 1.0)))))(
        chart => fx {
          val dots = chart.dots()
          val (x, _) = dots(0)(1)
          val high = dots(0)(1)._2
          val low = dots(1)(1)._2
          chart.markAt(x + 20.0, high + 5.0).map(m => (m.category, m.series)) shouldBe Some((1, 0))
          chart.markAt(x - 20.0, low - 5.0).map(m => (m.category, m.series)) shouldBe Some((1, 1))
          chart.markAt(-50.0, low).isEmpty shouldBe true
        })
    }
    "lay its points along a time axis, named by their days" in {
      val day = TimeScale.Day
      val start = everyui.date.CalendarDate(2026, 11, 10).toLocalEpochMillis
      val times = Vector(start, start + day, start + 3L * day)
      shown(LineChart.time(times, Vector(Series("Mail", Vector(5.0, 6.0, 7.0)))))(chart => fx {
        chart.categories() shouldBe Vector("Nov 10", "Nov 11", "Nov 13")
        val xs = chart.dots()(0).map(_._1)
        // the gap of two days twice the gap of one
        (xs(2) - xs(1)) shouldBe ((xs(1) - xs(0)) * 2.0) +- 0.01
      })
    }
  }

  "A donut chart" should {
    "find a slice by its angle, and say each part's share" in {
      shown(DonutChart(Vector("Meetings", "Focus"), Vector(30.0, 10.0)), 300.0, 200.0)(chart => {
        val r = fx(chart.ring())
        // a quarter of the way round: three o'clock, in the first slice (three quarters of the whole)
        fx(chart.markAt(r.right - 10.0, r.centerY).map(_.category)) shouldBe Some(0)
        fx(chart.markAt(r.x + 10.0, r.centerY - 5.0).map(_.category)) shouldBe Some(1)
        fx(chart.markAt(r.centerX, r.centerY)) shouldBe None
        fx(chart.semantics().label) shouldBe "Donut chart: Total 40. Meetings 75%, Focus 25%."
      })
    }
  }

  "A heatmap" should {
    "tell the app the row and column of the cell pointed at" in {
      shown(Heatmap(Vector("Mon", "Tue"), Vector("9", "10", "11"), Vector(Vector(1.0, 2.0, 3.0), Vector(4.0, 5.0, 6.0))))(
        chart => {
          val g = fx(chart.grid())
          val c = fx(chart.cell())
          fx(chart.pointAt(g.x + c.width * 1.5, g.y + c.height * 1.5, pressed = false))
          fx(chart.hovered()) shouldBe Some(Pointed("10", "Tue", 5.0, 1, 1))
        })
    }
  }

  "A meter, a gauge and a stat tile" should {
    "say their value to a screen reader, and their state past their thresholds" in {
      val spent = Var(620.0)
      shown(Meter("Budget", spent(), 1000.0, ChartFormat.money("$", 0)), 300.0, 40.0)(m => {
        fx(m.warnAt @= 0.8)
        fx(m.semantics().range.map(_.text)) shouldBe Some("$620 of $1,000")
        fx(m.semantics().range.map(_.now)) shouldBe Some(62.0)
        fx(m.state) shouldBe ""
        fx(spent @= 850.0)
        fx(m.state) shouldBe "warning"
        fx(m.fraction) shouldBe 0.85 +- 1e-9
      })
      shown(Gauge("Context", 150.0, 200.0), 160.0, 120.0)(g => fx(g.semantics().range.map(_.now)) shouldBe Some(75.0))
      shown(StatTile("Spend", "$412", Some(0.12), "vs last week"), 200.0, 100.0)(t => {
        fx(t.upIsGood @= false)
        fx(t.semantics().label) shouldBe "Spend: $412, up 12% vs last week"
        val moved = fx(all(t).collect { case v: everyui.component.TextView if v.content() == "12%" => v.color() })
        moved shouldBe List(fx(Theme.dangerText()))
        fx(t.upIsGood @= true)
        fx(all(t).collect { case v: everyui.component.TextView if v.content() == "12%" => v.color() }) shouldBe
          List(fx(Theme.successText()))
      })
    }
  }

  "The series colours" should {
    "be the palette's, light or dark, and the muted grey past eight" in {
      val was = fx(Theme.palette())
      try {
        fx(Theme.palette @= Palette.light)
        fx(Theme.series(0)) shouldBe everyui.Color.fromHex("2a78d6")
        fx(Theme.palette @= Palette.dark)
        fx(Theme.series(1)) shouldBe everyui.Color.fromHex("d95926")
        fx(Theme.series(8)) shouldBe Palette.dark.muted
        // an app's palette of its own takes the shipped ones for its scheme
        fx(Theme.palette @= Palette.light.copy(series = Vector.empty, accent = everyui.Color.fromHex("aa0000")))
        fx(Theme.series(2)) shouldBe everyui.Color.fromHex("1baf7a")
        fx(ChartTone.Slot(2).colour(0)) shouldBe everyui.Color.fromHex("1baf7a")
      }
      finally fx(Theme.palette @= was)
    }
  }

  "The charts' words" should {
    "be said in every language Words ships, never as their keys" in {
      val was = fx(Strings.language())
      try List("en", "de", "es", "fr", "it", "nl", "pt", "tr", "ar", "zh", "zh_Hant").foreach { code =>
        fx(Strings.language @= Language(code, code, code))
        val said = fx(List(ChartWords.bar, ChartWords.line, ChartWords.area, ChartWords.donut, ChartWords.pie,
          ChartWords.heatmap, ChartWords.gauge, ChartWords.category, ChartWords.other, ChartWords.total,
          ChartWords.value, ChartWords.share, ChartWords.showTable, ChartWords.showChart, ChartWords.range(3, "1", "9"),
          ChartWords.highest("Tue", "9"), ChartWords.of("1", "2"), ChartWords.up("3%"), ChartWords.down("3%"),
          ChartWords.noData))
        said.foreach { s =>
          withClue(code + ": ")(s.startsWith("chart.") shouldBe false)
          withClue(code + ": ")(s should not include "{")
        }
      }
      finally fx(Strings.language @= was)
    }
  }
}
