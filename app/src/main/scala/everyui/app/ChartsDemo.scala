package everyui.app

import everyui.chart.*
import everyui.component.{Button, Column, Component, Container, Row, ScrollContainer, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.date.CalendarDate
import everyui.i18n.Direction
import everyui.layout.FlowLayout
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Every chart (`everyui.chart`) on a daily briefing's data, in a grid of cards as wide as the window allows: spend per
  * day in money over a time axis and as bars, meeting hours by weekday side by side, mail per account stacked by day
  * and as ranked bars along, model use by day as lines, the week's time as a donut and mail as a pie, a week by hours
  * heatmap of meetings, a budget and credits as meters, a context gauge, and three stat tiles. New data eases every
  * chart to its next values (`live=1` every two seconds). `theme=light`, `rtl=1`; `hover=1` points at a bar, a day
  * of the lines and a slice, and shows a table. Logs `charts ok`, and
  * `charts pointed <category> | <series> | <value>` for each mark pointed at or pressed. */
object ChartsDemo {
  private val weekdays = Vector("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
  private val days7 = Vector("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val round = Var(0)
    def wave(i: Int, k: Int): Double = {
      val r = round()
      1.0 + 0.35 * math.sin((i + 1) * 1.7 + k * 2.3 + r * 0.9)
    }
    val today = CalendarDate(2026, 11, 18).toLocalEpochMillis
    val days30 = (0 until 30).toVector.map(i => today - (29 - i).toLong * 86400000L)
    def spend30: Vector[Double] = days30.indices.toVector.map(i =>
      math.round((24.0 + (if (i % 7 == 5) 38.0 else 0.0) + 9.0 * math.sin(i / 2.5)) * wave(i, 0) * 100.0) / 100.0)

    val spendLine = AreaChart.time(days30, Vector(Series("Spend", spend30)), ChartFormat.money("$"))
    val spendBars = BarChart(days7, Vector(Series("Spend", spend30.drop(30 - 7))), ChartFormat.money("$", 0))
    val meetings = BarChart(weekdays, Vector(
      Series("Meetings", weekdays.indices.toVector.map(i => math.round(Vector(3.5, 5.0, 2.0, 6.0, 4.0)(i) * wave(i, 1) * 2.0) / 2.0)),
      Series("1:1s", weekdays.indices.toVector.map(i => math.round(Vector(1.0, 0.5, 1.5, 1.0, 0.5)(i) * wave(i, 2) * 2.0) / 2.0)),
      Series("Focus", weekdays.indices.toVector.map(i => math.round(Vector(3.0, 2.0, 4.0, 1.0, 2.5)(i) * wave(i, 3) * 2.0) / 2.0))),
      ChartFormat.hours)
    val accounts = Vector("Work", "Personal", "Newsletters")
    val mailStacked = BarChart(days7, accounts.indices.toVector.map(k => Series(accounts(k),
      days7.indices.toVector.map(i => math.round(Vector(42.0, 12.0, 25.0)(k) * (if (i == 2 || i == 3) 0.4 else 1.0) *
        wave(i, k + 4)).toDouble))), ChartFormat.number(_, 0), stacked = true)
    val inboxes = Vector("matt@work.example.com", "personal@example.com", "newsletters@lists.example.org",
      "billing@example.com", "team-updates@work.example.com")
    val mailRanked = BarChart(inboxes, Vector(Series("Mail", inboxes.indices.toVector.map(i =>
      math.round(Vector(312.0, 128.0, 96.0, 41.0, 22.0)(i) * wave(i, 7)).toDouble))), ChartFormat.number(_, 0),
      horizontal = true)
    val days14 = days30.drop(16)
    val usage = LineChart.time(days14, Vector(
      Series("Large", days14.indices.toVector.map(i => math.round(180000.0 * wave(i, 8) + i * 9000.0).toDouble)),
      Series("Medium", days14.indices.toVector.map(i => math.round(120000.0 * wave(i, 9)).toDouble)),
      Series("Small", days14.indices.toVector.map(i => math.round(60000.0 * wave(i, 10) + (13 - i) * 4000.0).toDouble))),
      ChartFormat.compact, points = true)
    usage.fromZero @= false
    val week = DonutChart(Vector("Meetings", "Focus", "Mail", "Reviews", "Travel"),
      Vector(14.0 * wave(0, 11), 18.5 * wave(1, 11), 6.0 * wave(2, 11), 4.0 * wave(3, 11), 2.0).map(v => math.round(v * 2.0) / 2.0),
      ChartFormat.hours)
    week.caption @= "This week"
    val mailPie = PieChart(accounts, Vector(312.0 * wave(0, 12), 128.0 * wave(1, 12), 96.0).map(v => math.round(v).toDouble),
      ChartFormat.number(_, 0))
    val hours = (0 until 24).toVector.map(h => h.toString)
    val busy = Heatmap(Vector("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), hours,
      (0 until 7).toVector.map(d => hours.indices.toVector.map { h =>
        val work = h >= 9 && h <= 17 && d < 5
        val v = if (work) 2.0 + 2.0 * math.sin(h * 0.9 + d) * wave(h, d) else if (h >= 7 && h <= 21) 0.4 * wave(h, d + 2) else 0.0
        math.max(0.0, math.round(v * 2.0) / 2.0)
      }), ChartFormat.number(_, 1))
    val budget = Meter("Monthly budget", math.round(620.0 * wave(0, 13)).toDouble, 1000.0, ChartFormat.money("$", 0))
    budget.warnAt @= 0.8
    budget.dangerAt @= 1.0
    val credits = Meter("API credits", math.round(920.0 * wave(2, 14)).toDouble, 1000.0, ChartFormat.number(_, 0))
    credits.warnAt @= 0.8
    credits.dangerAt @= 0.95
    val context = Gauge("Context used", math.round(142000.0 * wave(1, 15)).toDouble, 200000.0)
    context.warnAt @= 0.75
    context.dangerAt @= 0.9
    val weekSpend = spend30.drop(30 - 7).sum
    val tiles = List(
      StatTile("Spend this week", ChartFormat.money("$")(weekSpend), Some(0.12 * wave(0, 16)), "vs last week"),
      StatTile("Meeting hours", ChartFormat.number(18.5 * wave(1, 16), 1), Some(-0.08), "vs last week"),
      StatTile("Mail received", ChartFormat.number(1284.0 * wave(2, 16), 0), Some(0.04), "vs last week"))
    tiles.head.upIsGood @= false
    tiles.head.trend := spend30.drop(30 - 12)
    tiles(1).upIsGood @= false
    tiles(1).trend := Vector(22.0, 19.5, 21.0, 24.0, 20.0, 18.5).map(_ * wave(3, 17))
    tiles(2).trend := Vector(1100.0, 1180.0, 1210.0, 1150.0, 1240.0, 1284.0).map(_ * wave(4, 17))

    val charts: List[Chart] = List(spendLine, spendBars, meetings, mailStacked, mailRanked, usage, week, mailPie, busy)
    charts.foreach(c => c.pointed.attach(p => Platform.log("charts pointed " + p.category + " | " + p.series + " | " +
      p.value.toString + (if (p.pressed) " pressed" else ""))))

    val wide = Val(ui.size.width())
    val columns = Val(math.max(1, ((wide() - 32.0 + 16.0) / (400.0 + 16.0)).toInt))
    val cardWidth = Val((wide() - 32.0 - 16.0 * (columns() - 1)) / columns())
    def card(title: String, body: Component, tall: Double): Container = {
      val heading = TextView(title, 14.0, Theme.text())
      heading.font.weight @= FontWeight.SemiBold
      heading.semantics @= Semantics.heading(2)
      body.size.width := cardWidth() - 32.0
      if (tall > 0.0) body.size.height @= tall
      val c = Column(12.0, padding = 16.0)(heading, body)
      c.size.width := cardWidth()
      c.backgroundColor := Theme.surface()
      c.radius @= 12.0
      c.semantics @= Semantics.region(title)
      c
    }
    def stack(parts: Component*): Container = {
      val c = Column(20.0)(parts*)
      parts.foreach(p => p.size.width := cardWidth() - 32.0)
      c
    }
    val tileRow = new Container {
      layout @= Some(FlowLayout(24.0, 16.0))
      size.height := contentHeight()
    }
    tiles.foreach(t => t.size.width := math.max(120.0, math.floor((cardWidth() - 32.0 - 48.0) / 3.0)))
    tileRow.children ++= tiles
    val cards = List(
      card("Spend per day", spendLine, 220.0), card("Spend, last 7 days", spendBars, 220.0),
      card("Meeting hours by weekday", meetings, 240.0), card("Mail per account, by day", mailStacked, 240.0),
      card("Mail per account, 30 days", mailRanked, 220.0), card("Model use, tokens per day", usage, 240.0),
      card("Time this week", week, 220.0), card("Mail by account", mailPie, 220.0),
      card("Meetings, week by hour", busy, 240.0), card("Briefing", tileRow, 0.0),
      card("Budget and credits", stack(budget, credits), 0.0), card("Context", context, 0.0))
    context.size.width @= 180.0
    val grid = new Container {
      layout @= Some(FlowLayout(16.0, 16.0))
      size.width := wide() - 32.0
      size.height := contentHeight()
      position.left @= 16.0
      position.top @= 8.0
    }
    grid.children ++= cards
    val again = new Button("New data", () => round @= round() + 1)
    val theme = new Button("Light / dark", () =>
      Theme.palette @= (if (Theme.palette() eq Palette.light) Palette.dark else Palette.light))
    val bar = Row(10.0, middle = true)(again, theme)
    bar.position.left @= 16.0
    bar.position.top @= 12.0
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height() - 56.0
      position.top @= 56.0
    }
    scroll.content.children += grid
    ui.children ++= List(bar, scroll)
    if (Platform.launchArg("live") == "1") {
      def tick(): Unit = Platform.after(2.0, () => {
        round @= round() + 1
        tick()
      })
      tick()
    }
    // for a look at the tips and the table: a bar, a day of the lines and a slice pointed at, a table shown
    if (Platform.launchArg("hover") == "1") Platform.after(0.5, () => {
      meetings.bars().find(b => b._1.category == 3 && b._1.series == 0).foreach(b =>
        meetings.pointAt(b._1.centerX, b._1.centerY, pressed = false))
      usage.dots().headOption.flatMap(_.lift(6)).foreach(p => usage.pointAt(p._1, p._2, pressed = false))
      val r = week.ring()
      week.pointAt(r.right - 6.0, r.centerY, pressed = false)
      mailStacked.table @= true
    })
    Platform.log("charts ok " + charts.size.toString + " charts")
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }
}
