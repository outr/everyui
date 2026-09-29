package everyui.date

import everyui.component.types.Semantics
import everyui.component.{Button, ButtonStyle, Container, IconButton, TextView}
import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.{DateText, Direction, WeekStart}
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A month on a calendar, `width` wide, in shared EveryUI: its name over the days of the week and a grid of its days,
  * with buttons to the month before and after, and the name to press for a grid of years to jump to. Today is ringed,
  * the days outside `min` and `max` are muted and cannot be chosen, and `marks` says how a selection draws each day
  * (`DayMark`: DatePicker's chosen day, DateRangePicker's ends and the band between) from the day and the one the pointer
  * or the focus is on (`pointing`). Choosing a day (a click, a tap, OK or Enter) calls `choose`.
  *
  * The keyboard moves the focus among the days as a grid does (`CalendarKeys`): the arrows a day or a week, Page Up
  * and Page Down a month (a year with Shift), Home and End to the week's ends, the month turning as the focus leaves
  * it; Tab goes from the header's buttons into the grid and out, which is one stop. On a remote, Up from the first week
  * goes to the header and Down from the last one out of the calendar, rather than into the next month; Left and Right
  * still turn the month. In a right-to-left language the grid and the header mirror, and Left and Right with them.
  *
  * To a screen reader the grid is a `grid` of `row`s of `gridcell`s, each named with its whole date ("Tuesday,
  * September 29, 2026"), and whether it is today or selected; the days of the week are `columnheader`s.
  *
  * `active` is the day the focus is on, or lands on as it arrives: the month shown is its month. `show` moves it.
  *
  * {{{
  *   val chosen = Var(Option.empty[CalendarDate])
  *   val calendar = new Calendar((day, _) => DayMark(selected = chosen().contains(day)), day => chosen @= Some(day))
  * }}} */
class Calendar(marks: (CalendarDate, Option[CalendarDate]) => DayMark, choose: CalendarDate => Unit,
               start: CalendarDate = CalendarDate.today(), val min: Option[CalendarDate] = None,
               val max: Option[CalendarDate] = None, val firstDay: Int = WeekStart.current(),
               val today: CalendarDate = CalendarDate.today(), width: Double = Calendar.width,
               val points: Double = Calendar.textSize()) extends Container {
  /** The day the focus is on, or lands on: the month shown is its month. */
  val active: Var[CalendarDate] = own(Var(start.clamp(min, max)))
  /** The first day of the month shown. */
  val shown: Val[CalendarDate] = own(Val(active().firstOfMonth))
  /** The first day of the grid: the start of the week the month starts in. */
  val gridStart: Val[CalendarDate] = own(Val(shown().startOfWeek(firstDay)))
  /** Whether the grid of years is up in place of the days. */
  val choosingYear: Var[Boolean] = own(Var(false))
  /** The first year on the grid of years, and the year the focus is on there. */
  val yearsFrom: Var[Int] = own(Var(Calendar.pageOf(start.year)))
  val activeYear: Var[Int] = own(Var(start.year))
  /** The day the pointer is over, if any. */
  val pointed: Var[Option[CalendarDate]] = own(Var(None))
  private val focusedDay: Var[Boolean] = own(Var(false))
  /** The day the pointer is over, else the day with the focus: what a range's preview reaches to. */
  val pointing: Val[Option[CalendarDate]] = own(Val(pointed().orElse(if (focusedDay()) Some(active()) else None)))

  private val cellWidth: Double = width / 7.0
  private val rowHeight: Double = math.round(points * 2.9).toDouble
  private val headerHeight: Double = math.round(points * 3.4).toDouble
  private val weekdayHeight: Double = math.round(points * 2.3).toDouble

  private val title: Button = Button.bound(
    (if (choosingYear()) Calendar.years(yearsFrom()) else DateText.monthYear(shown().year, shown().month)) +
      (if (choosingYear()) " ▴" else " ▾"),
    () => toggleYears(), ButtonStyle.Text, points = points)
  private val rtl: Boolean = Direction.rtl()
  private val previous = new IconButton(if (rtl) MaterialIcon.ChevronRight else MaterialIcon.ChevronLeft,
    DateWords.previousMonth, () => page(-1))
  private val next = new IconButton(if (rtl) MaterialIcon.ChevronLeft else MaterialIcon.ChevronRight,
    DateWords.nextMonth, () => page(1))
  private val gap = new Container
  private val header = new Container
  /** The days, a week to a row. */
  val days: List[CalendarDay] = (0 until 42).toList.map(i => new CalendarDay(this, i, cellWidth, rowHeight))
  private val weeks: List[Container] = (0 until 6).toList.map { w =>
    val row = new Container
    row.layout @= Some(HorizontalLayout(0.0))
    row.semantics @= Semantics("row")
    row.children ++= days.slice(w * 7, w * 7 + 7)
    row
  }
  private val grid = new Container
  private val weekdays = new Container
  private val yearGrid = new Container
  /** The years on the grid of years, `Calendar.pageYears` at a time: made the first time the grid is up, since most
    * calendars never show it. */
  lazy val years: List[CalendarYear] = {
    val made = (0 until Calendar.pageYears).toList.map(i => new CalendarYear(this, i, width / Calendar.yearColumns,
      rowHeight * 6.0 / (Calendar.pageYears / Calendar.yearColumns)))
    (0 until Calendar.pageYears / Calendar.yearColumns).foreach { r =>
      val row = new Container
      row.layout @= Some(HorizontalLayout(0.0))
      row.semantics @= Semantics("row")
      row.children ++= made.slice(r * Calendar.yearColumns, r * Calendar.yearColumns + Calendar.yearColumns)
      yearGrid.children += row
    }
    made
  }

  size.width @= width
  size.height @= headerHeight + weekdayHeight + rowHeight * 6.0

  // the header: the month's name at the start, the buttons at the end, mirrored with the row
  previous.tooltip := (if (choosingYear()) DateWords.previousYears else DateWords.previousMonth)
  next.tooltip := (if (choosingYear()) DateWords.nextYears else DateWords.nextMonth)
  previous.semantics := Semantics.button(previous.tooltip())
  next.semantics := Semantics.button(next.tooltip())
  title.semantics := Semantics.button(if (choosingYear()) DateWords.chooseYear else DateWords.chooseYear + ", " +
    DateText.monthYear(shown().year, shown().month))
  gap.size.height @= 1.0
  gap.size.width := math.max(0.0, width - title.size.width() - previous.size.width() - next.size.width() - 8.0)
  header.layout @= Some(HorizontalLayout(4.0, middle = true))
  header.children ++= List(title, gap, previous, next)
  header.position.top := (headerHeight - header.size.height()) / 2.0
  previous.visible := !choosingYear() || yearsFrom() > min.map(_.year).getOrElse(Int.MinValue)
  next.visible := !choosingYear() || yearsFrom() + Calendar.pageYears <= max.map(_.year).getOrElse(Int.MaxValue)
  previous.disabled := !choosingYear() && min.exists(m => !shown().isAfter(m.firstOfMonth))
  next.disabled := !choosingYear() && max.exists(m => !shown().isBefore(m.firstOfMonth))
  previous.opacity := (if (previous.disabled()) 0.38 else 1.0)
  next.opacity := (if (next.disabled()) 0.38 else 1.0)

  // the days of the week, from the first
  weekdays.position.top @= headerHeight
  weekdays.layout @= Some(HorizontalLayout(0.0))
  weekdays.semantics @= Semantics("row")
  weekdays.visible := !choosingYear()
  (0 until 7).foreach { i =>
    val weekday = CalendarDate.floorMod(firstDay - 1 + i, 7) + 1
    val box = new Container
    val letter = TextView.bound(DateText.narrowWeekday(weekday), points * 0.9, Theme.muted())
    box.size.width @= cellWidth
    box.size.height @= weekdayHeight
    box.semantics := Semantics("columnheader", label = DateText.weekdayName(weekday))
    letter.position.center := cellWidth / 2.0
    letter.position.middle := weekdayHeight / 2.0
    box.children += letter
    weekdays.children += box
  }

  grid.position.top @= headerHeight + weekdayHeight
  grid.layout @= Some(VerticalLayout(0.0))
  grid.semantics := Semantics("grid", label = DateText.monthYear(shown().year, shown().month))
  grid.visible := !choosingYear()
  grid.children ++= weeks

  yearGrid.position.top @= headerHeight
  yearGrid.size.width @= width
  yearGrid.size.height @= weekdayHeight + rowHeight * 6.0
  yearGrid.semantics @= Semantics("grid", label = DateWords.chooseYear)
  yearGrid.visible := choosingYear()
  yearGrid.layout @= Some(VerticalLayout(weekdayHeight / (Calendar.pageYears / Calendar.yearColumns)))
  choosingYear.attach(on => if (on) years.size)

  children ++= List(header, weekdays, grid, yearGrid)

  // the hand-offs a remote needs: down from the header into the grid, up from the grid to the header
  header.exitAction(Key.ArrowDown, () => focusBody())
  grid.exitAction(Key.ArrowUp, () => title.focus())
  yearGrid.exitAction(Key.ArrowUp, () => title.focus())

  /** Show `date`'s month with the focus to land on it (in range), and the days again if the years were up. */
  def show(date: CalendarDate): Unit = {
    choosingYear @= false
    active @= date.clamp(min, max)
  }

  /** The day with the focus, or the year on the grid of years: where Tab or an arrow into the calendar lands. */
  def focusBody(): Unit =
    if (choosingYear()) years.find(_.focusable()).foreach(_.focus()) else dayOf(active()).foreach(_.focus())

  /** The cell showing `date`, when it is in the month shown. */
  def dayOf(date: CalendarDate): Option[CalendarDay] = {
    val i = gridStart().daysUntil(date)
    if (i >= 0 && i < 42 && date.sameMonth(shown())) Some(days(i)) else None
  }

  /** Whether `date` can be chosen. */
  def allowed(date: CalendarDate): Boolean = date.within(min, max)

  /** How `date` is drawn. */
  def mark(date: CalendarDate): DayMark = marks(date, pointing())

  /** `date` chosen: the focus to it, and `choose` told. */
  def pick(date: CalendarDate): Unit = if (allowed(date)) {
    active @= date
    dayOf(date).foreach(_.focus())
    choose(date)
  }

  /** The months (or the pages of years) `by` on. */
  def page(by: Int): Unit =
    if (choosingYear()) {
      yearsFrom @= yearsFrom() + by * Calendar.pageYears
      activeYear @= math.max(yearsFrom(), math.min(yearsFrom() + Calendar.pageYears - 1, activeYear() + by * Calendar.pageYears))
    }
    else active @= active().plusMonths(by).clamp(min, max)

  private def toggleYears(): Unit = {
    if (!choosingYear()) {
      activeYear @= active().year
      yearsFrom @= Calendar.pageOf(active().year)
    }
    choosingYear @= !choosingYear()
  }

  /** `year` chosen on the grid of years: its month as the one shown, the day kept (in range). */
  def pickYear(year: Int): Unit = {
    val a = active()
    val day = math.min(a.day, CalendarDate.daysInMonth(year, a.month))
    val focusWasHere = Focus.focused().exists(f => Focus.within(f, yearGrid))
    choosingYear @= false
    active @= CalendarDate(year, a.month, day).clamp(min, max)
    if (focusWasHere) focusBody()
  }

  /** Whether the day cell keeps `e`: a key that moves among the days, except, on a remote, Up and Down out of the
    * month, which leave the grid. */
  private[date] def keepsDay(e: KeyEvent): Boolean =
    CalendarKeys.moves(e) && !(Platform.remoteNavigation() && (e.key == Key.ArrowUp || e.key == Key.ArrowDown) &&
      CalendarKeys.target(active(), e, Direction.rtl(), firstDay).exists(t => !t.sameMonth(active())))

  /** A key on the day cell with the focus: the focus to the day it moves to, turning the month where it leaves it. */
  private[date] def dayKey(e: KeyEvent): Unit = if (keepsDay(e))
    CalendarKeys.target(active(), e, Direction.rtl(), firstDay).map(_.clamp(min, max)).foreach { to =>
      if (to != active()) {
        active @= to
        dayOf(to).foreach(_.focus())
      }
    }

  private[date] def dayFocused(): Unit = focusedDay @= days.exists(_.focused())

  /** Whether a year cell keeps `e`, as a day cell does. */
  private[date] def keepsYear(e: KeyEvent): Boolean =
    CalendarKeys.moves(e) && !(Platform.remoteNavigation() && (e.key == Key.ArrowUp || e.key == Key.ArrowDown) &&
      yearTarget(e).exists(y => y < yearsFrom() || y >= yearsFrom() + Calendar.pageYears))

  /** A key on the year with the focus: a year along, a row up or down, a page with Page Up and Page Down, the page's
    * ends with Home and End; the page turning as the focus leaves it. */
  private[date] def yearKey(e: KeyEvent): Unit = if (keepsYear(e)) yearTarget(e).foreach { to =>
    val low = min.map(_.year).getOrElse(Int.MinValue)
    val high = max.map(_.year).getOrElse(Int.MaxValue)
    val year = math.max(low, math.min(high, to))
    if (year != activeYear()) {
      activeYear @= year
      if (year < yearsFrom() || year >= yearsFrom() + Calendar.pageYears) yearsFrom @= Calendar.pageOf(year)
      years.find(_.year() == year).foreach(_.focus())
    }
  }

  private def yearTarget(e: KeyEvent): Option[Int] = {
    val on = if (Direction.rtl()) -1 else 1
    val y = activeYear()
    if (e.key == Key.ArrowLeft) Some(y - on)
    else if (e.key == Key.ArrowRight) Some(y + on)
    else if (e.key == Key.ArrowUp) Some(y - Calendar.yearColumns)
    else if (e.key == Key.ArrowDown) Some(y + Calendar.yearColumns)
    else if (e.key == Key.PageUp) Some(y - Calendar.pageYears)
    else if (e.key == Key.PageDown) Some(y + Calendar.pageYears)
    else if (e.name == "Home") Some(yearsFrom())
    else if (e.name == "End") Some(yearsFrom() + Calendar.pageYears - 1)
    else None
  }

  override def kind: String = "Calendar"
}

object Calendar {
  /** How wide a calendar is unless it says: seven days of 46 pixels. */
  val width: Double = 322.0
  /** How big a calendar's words are: a television's app sets it larger once, before it makes any. */
  val textSize: Var[Double] = Var(14.0)
  /** How many years the grid of years holds at a time, and in how many columns. */
  val pageYears: Int = 20
  val yearColumns: Int = 4

  /** The first year of the page of years `year` is on. */
  def pageOf(year: Int): Int = CalendarDate.floorDiv(year, pageYears) * pageYears

  /** "2020 – 2039": a page of years. */
  def years(from: Int): String = from.toString + " – " + (from + pageYears - 1).toString
}
