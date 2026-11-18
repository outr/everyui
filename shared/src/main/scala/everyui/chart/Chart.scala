package everyui.chart

import everyui.Color
import everyui.component.{Container, IconButton, Row, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.draw.Drawing
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.Mirror
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** What every chart has: its size, which it fills (`size.width`, `size.height`: give it one, and it follows it); a
  * legend over it for two series or more; a button at the top end that shows its data as a table in its place, and
  * back (`table`, `tableToggle`); what it says to a screen reader (`summary`, a short description of what it shows, made
  * from the data unless the app gives its own); and the marks the pointer, a press or the arrow keys land on, told to
  * the app (`pointed`, `hovered`) with the theme's own tip beside them unless `tips` is off.
  *
  * {{{
  *   chart.pointed.attach(p => if (p.pressed) openDay(p.categoryIndex))
  *   chart.tips @= false                                   // the app's own tip instead, from `hovered`
  * }}} */
abstract class Chart extends Container {
  /** How a value is written: on the value axis, in the tips and in the table. */
  val format: Var[Double => String] = own(Var(ChartFormat.compact))
  /** Each mark as the pointer comes onto it, a press lands on it (`Pointed.pressed`), or the arrow keys move to it. */
  val pointed: Channel[Pointed] = Channel[Pointed]
  /** The mark the pointer or the keys are on now, None where none is. */
  val hovered: Var[Option[Pointed]] = own(Var(None))
  /** Whether the chart shows its own tip by the mark `hovered` is on. */
  val tips: Var[Boolean] = own(Var(true))
  /** What a screen reader says the chart shows; empty, the chart says what it is, its series and the range of its
    * values. */
  val summary: Var[String] = own(Var(""))
  /** Whether the data shows as a table in place of the drawing. */
  val table: Var[Boolean] = own(Var(false))
  /** Whether the button that shows the table, and back, is there (off where the app offers the table its own way). */
  val tableToggle: Var[Boolean] = own(Var(true))

  /** What the chart is, in words ("Bar chart"). */
  protected def kindWords: String
  /** The data as a table. */
  protected def tableNow: ChartTable
  /** What the chart shows, in a sentence, where the app gives no `summary`. */
  protected def described: String
  /** The legend's lines: none for one series, as the title names it. */
  protected def legendEntries: Vector[LegendEntry] = Vector.empty
  /** Where each mark is heard, in the plot's pixels. */
  protected def marks: Vector[ChartMark]
  /** What mark `m` is. */
  protected def pointedOf(m: ChartMark, pressed: Boolean): Pointed
  /** A tip's title and its lines for `p`: each a key's colour, the value and whose it is. */
  protected def tipOf(p: Pointed): (String, Vector[(Color, String, String)])

  /** The mark at (`x`, `y`) in the plot's pixels, None where there is none. */
  def markAt(x: Double, y: Double): Option[ChartMark] = ChartMark.at(marks, x, y)

  /** The drawing the marks are in, under the legend. */
  val plot: Drawing = new Drawing
  /** The axes' words, over the plot. */
  protected val words: Container = new Container

  private var entries: Val[Vector[LegendEntry]] = null
  private var legendShown: Val[Boolean] = null
  private var legendMade: Option[Legend] = None
  private var toggleMade: Option[IconButton] = None
  private var tableMade: Option[ChartTableView] = None
  private var tableData: Option[Val[ChartTable]] = None
  private var tipMade: Option[Container] = None

  /** How far down the plot starts: under the legend and the toggle, where they are. */
  val head: Var[Double] = own(Var(0.0))

  /** The plot's size. */
  def plotWidth: Double = size.width()
  def plotHeight: Double = math.max(0.0, size.height() - head())

  clipsToBox @= Some(false)
  plot.position.top := head()
  plot.size.width := plotWidth
  plot.size.height := plotHeight
  words.position.top := head()
  words.size.width := plotWidth
  words.size.height := plotHeight
  words.clipsToBox @= Some(false)
  plot.visible := !table()
  words.visible := !table()
  children ++= List(plot, words)

  /** What reads the kind's own data, bound once that is made: called last by each kind's constructor. */
  protected final def ready(): Unit = {
    semantics := Semantics("figure", label = if (summary().nonEmpty) summary() else described)
    entries = own(Val(legendEntries))
    legendShown = own(Val(entries().size >= 2 && !table()))
    // the legend and the toggle, each made the first time it shows
    legendShown.attachAndFire(on => if (on && legendMade.isEmpty) {
      val l = Legend(entries())
      l.size.width := size.width() - (if (tableToggle()) 44.0 else 0.0)
      Mirror.place(l, () => size.width(), () => 0.0)
      l.visible := legendShown()
      legendMade = Some(l)
      children += l
      measureHead()
    })
    tableToggle.attachAndFire(on => if (on && toggleMade.isEmpty) {
      val b = new IconButton(MaterialIcon.TableChart, ChartWords.showTable, () => table @= !table())
      b.icon.icon := (if (table()) MaterialIcon.BarChart else MaterialIcon.TableChart)
      b.tooltip := (if (table()) ChartWords.showChart else ChartWords.showTable)
      b.semantics := Semantics.button(if (table()) ChartWords.showChart else ChartWords.showTable)
      Mirror.place(b, () => size.width(), () => size.width() - b.size.width())
      b.position.top @= -4.0
      b.visible := tableToggle()
      toggleMade = Some(b)
      children += b
      measureHead()
    })
    table.attachAndFire(on => if (on) {
      hovered @= None
      // the data as a table, followed from the first time it shows, and the table made again as the data changes
      if (tableData.isEmpty) {
        val data = own(Val(tableNow))
        tableData = Some(data)
        data.attach(_ => showTable())
      }
      if (tableMade.isEmpty) showTable()
    })
  }

  private def showTable(): Unit = tableData.foreach { data =>
    tableMade.foreach { old =>
      children -= old
      old.dispose()
    }
    val t = new ChartTableView(data())
    t.position.top := head()
    t.size.width := plotWidth
    t.size.height := plotHeight
    t.visible := table()
    tableMade = Some(t)
    children += t
  }

  private def measureHead(): Unit = head := {
    val l = legendMade.filter(_ => legendShown()).map(_.size.height()).getOrElse(0.0)
    val t = toggleMade.filter(_ => tableToggle()).map(_ => 24.0).getOrElse(0.0)
    val tall = math.max(l, t)
    if (tall > 0.0) tall + 10.0 else 0.0
  }

  // the pointer over the plot, and presses on it
  plot.event.pointer.move.attach(e => pointAt(e.x - plot.absoluteLeft, e.y - plot.absoluteTop, pressed = false))
  plot.event.pointer.down.attach(e => pointAt(e.x - plot.absoluteLeft, e.y - plot.absoluteTop, pressed = true))
  plot.event.pointer.leave.attach(_ => if (!focused()) hovered @= None)

  /** What a pointer at (`x`, `y`) in the plot does (`pressed`: a press there): the mark there becomes `hovered`, and
    * is told to `pointed` as it is newly pointed at or pressed; nowhere, nothing is hovered. */
  def pointAt(x: Double, y: Double, pressed: Boolean): Unit = markAt(x, y) match {
    case Some(m) =>
      val p = pointedOf(m, pressed)
      val changed = !hovered().exists(h => h.categoryIndex == p.categoryIndex && h.seriesIndex == p.seriesIndex)
      hovered @= Some(p.copy(pressed = false))
      if (changed || pressed) pointed @= p
    case None => hovered @= None
  }

  // the arrow keys move along the marks, where a pointer and keys are the way about (not a remote's d-pad, whose
  // arrows move on from the chart), Escape lets go
  focusable @= !Platform.remoteNavigation()
  borderWidth := (if (focusShown()) 2.0 else 0.0)
  borderColor := Theme.accent()
  radius @= 6.0
  focused.attach(f => if (!f && !plot.event.pointer.over()) hovered @= None)
  event.key.down.attach(e => moved(e.key).foreach { m =>
    val p = pointedOf(m, pressed = false)
    hovered @= Some(p)
    pointed @= p
  })
  override def keepsKey(key: Key): Boolean = !table() && moved(key).nonEmpty

  /** The mark the arrow `key` moves to from the one `hovered` is on: across the categories, or between the series at
    * one; the first where none is. */
  private def moved(key: Key): Option[ChartMark] = {
    val all = marks.sortBy(m => (m.category, m.series))
    if (all.isEmpty) None
    else hovered() match {
      case None => if (key == Key.ArrowLeft || key == Key.ArrowRight || key == Key.ArrowUp || key == Key.ArrowDown)
          all.headOption else None
      case Some(h) =>
        val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
        val on = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
        val categories = all.map(_.category).distinct
        val at = categories.indexOf(h.categoryIndex)
        if (key == on || key == back) {
          val next = at + (if (key == on) 1 else -1)
          if (next < 0 || next >= categories.size) None
          else {
            val there = all.filter(_.category == categories(next))
            there.find(_.series == h.seriesIndex).orElse(there.headOption)
          }
        }
        else if (key == Key.ArrowUp || key == Key.ArrowDown) {
          val here = all.filter(_.category == h.categoryIndex)
          val i = here.indexWhere(_.series == h.seriesIndex) + (if (key == Key.ArrowDown) 1 else -1)
          if (i < 0 || i >= here.size) None else Some(here(i))
        }
        else if (key == Key.Escape) {
          hovered @= None
          None
        }
        else None
    }
  }

  // the tip, made the first time a mark is pointed at, by that mark
  hovered.attach { h =>
    if (h.nonEmpty && tips() && tipMade.isEmpty) {
      val plate = new Container {
        backgroundColor := Theme.raised()
        borderWidth @= 1.0
        borderColor := Theme.border()
        radius @= 6.0
        shadow @= Some(everyui.Shadow(0.0, 3.0, 12.0, Color.Black.withAlpha(0.3)))
        visible := tips() && hovered().nonEmpty && !table()
      }
      tipMade = Some(plate)
      children += plate
    }
    tipMade.foreach(plate => h.filter(_ => tips()).foreach(p => fillTip(plate, p)))
  }

  private def fillTip(plate: Container, p: Pointed): Unit = {
    val (title, lines) = tipOf(p)
    val heading = Chart.words(TextView(title, 12.0, Theme.muted()))
    val rows = lines.map { case (c, value, name) =>
      val key = new Container {
        size.width @= 12.0
        size.height @= 3.0
        radius @= 1.5
        backgroundColor @= c
      }
      val v = Chart.words(TextView(value, 13.0, Theme.text()))
      v.font.weight @= FontWeight.SemiBold
      Row(8.0, middle = true)((if (name.isEmpty) List(key, v) else List(key, v, Chart.words(TextView(name, 12.0, Theme.muted()))))*)
    }
    val column = everyui.component.Column(4.0)((heading +: rows)*)
    column.position.left @= 10.0
    column.position.top @= 7.0
    plate.children.replaceMaking(Vector(column))
    plate.size.width := column.size.width() + 20.0
    plate.size.height := column.size.height() + 14.0
    val m = marks.find(m => m.category == p.categoryIndex && m.series == p.seriesIndex)
    val cx = m.map(_.centerX).getOrElse(plotWidth / 2.0)
    val top = m.map(_.y).getOrElse(0.0)
    val bottom = m.map(m => m.y + m.height).getOrElse(0.0)
    plate.position.left := math.max(0.0, math.min(size.width() - plate.size.width(), cx - plate.size.width() / 2.0))
    plate.position.top := {
      val above = head() + top - plate.size.height() - 6.0
      if (above >= 0.0) above else math.min(size.height() - plate.size.height(), head() + bottom + 6.0)
    }
  }
}

object Chart {
  /** `t` with its words running their own way (`TextDirection.Auto`): a value, a name or a date the app wrote reads
    * as it is, "10 h" and "$1,000" left to right in a right-to-left app, an Arabic name right to left in any. */
  def words[T <: everyui.component.TextView](t: T): T = {
    t.direction @= everyui.component.types.TextDirection.Auto
    t
  }

  /** Series `s`'s value at category `i`: 0 where it has none. */
  def valueAt(s: Series, i: Int): Double = if (i >= 0 && i < s.values.size) s.values(i) else 0.0

  /** The colour of series `i` of `all`, as its tone says. */
  def colour(all: Vector[Series], i: Int): Color = all(i).tone.colour(i)

  /** What a chart of `kind` over `categories` and `series` shows, in a sentence: "Bar chart: Spend. 7 values, from $0
    * to $48; highest Wed, $48." */
  def describe(kind: String, categories: Vector[String], series: Vector[Series], format: Double => String): String = {
    val values = series.flatMap(s => categories.indices.map(i => valueAt(s, i)))
    val named = series.map(_.name).filter(_.nonEmpty).mkString(", ")
    val head = if (named.isEmpty) kind else kind + ": " + named
    if (values.isEmpty) head + ". " + ChartWords.noData
    else {
      var best = 0
      var bestSeries = 0
      series.indices.foreach(s => categories.indices.foreach(i =>
        if (valueAt(series(s), i) > valueAt(series(bestSeries), best)) {
          best = i
          bestSeries = s
        }))
      val top = valueAt(series(bestSeries), best)
      val where = if (series.size > 1) categories(best) + " (" + series(bestSeries).name + ")" else categories(best)
      head + ". " + ChartWords.range(values.size, format(values.min), format(values.max)) + "; " +
        ChartWords.highest(where, format(top)) + "."
    }
  }
}
