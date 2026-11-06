package everyui.date

import everyui.Color
import everyui.component.types.Semantics
import everyui.component.{Container, TextView}
import everyui.event.KeyEvent
import everyui.i18n.{DateText, Direction}
import everyui.theme.Theme
import reactify.*

/** One day of a calendar's grid, the `index`th from its first: its number in a circle, filled while it is chosen,
  * ringed while it is today or has the focus, with the band of a range behind it (`DayMark`). A day outside the month
  * shown is not drawn, and one outside the calendar's `min` and `max` is muted and does nothing. Only the day the
  * calendar's focus is on takes the focus (the grid is one stop for Tab); the keys move it (`Calendar.dayKey`). */
class CalendarDay(calendar: Calendar, index: Int, cellWidth: Double, cellHeight: Double) extends Container {
  /** The day it shows. */
  val date: Val[CalendarDate] = own(Val(calendar.gridStart().plusDays(index)))
  private val inMonth: Val[Boolean] = own(Val(date().sameMonth(calendar.shown())))
  private val allowed: Val[Boolean] = own(Val(calendar.allowed(date())))
  private val mark: Val[DayMark] = own(Val(if (inMonth()) calendar.mark(date()) else DayMark.plain))
  private val today: Val[Boolean] = own(Val(date() == calendar.today))
  private val lit: Val[Boolean] = own(Val(allowed() && (focusShown() || event.pointer.over())))
  private val diameter: Double = math.min(cellWidth, cellHeight) - 4.0
  private val band = new Container
  private val circle = new Container
  private val number = new TextView

  size.width @= cellWidth
  size.height @= cellHeight
  visible := inMonth()
  focusable := inMonth() && !calendar.choosingYear() && date() == calendar.active()
  disabled := !allowed()
  semantics := Semantics("gridcell", label = DateText.fullDate(date().year, date().month, date().day, date().weekday) +
    (if (today()) ", " + DateWords.today else "") + (if (mark().selected) ", " + DateWords.selected else ""))

  // the band runs toward the day before on its reading side (the left, or the right in a right-to-left language)
  band.size.height @= diameter
  band.position.top @= (cellHeight - diameter) / 2.0
  band.size.width := (if (mark().before && mark().after) cellWidth else if (mark().before || mark().after) cellWidth / 2.0
    else 0.0)
  band.position.left := (if (mark().before == mark().after) 0.0
    else if (mark().before != Direction.rtl()) 0.0 else cellWidth / 2.0)
  band.backgroundColor := Theme.accent().withAlpha(if (mark().preview) 0.14 else 0.26)

  circle.size.width @= diameter
  circle.size.height @= diameter
  circle.radius @= diameter / 2.0
  circle.position.left @= (cellWidth - diameter) / 2.0
  circle.position.top @= (cellHeight - diameter) / 2.0
  circle.backgroundColor := (if (mark().selected) Theme.accent() else if (lit()) Theme.controlLit() else Color.Clear)
  circle.borderWidth := (if (focusShown()) 2.5 else if (today()) 1.0 else 0.0)
  circle.borderColor := (if (focusShown() && mark().selected) Theme.text() else Theme.accent())

  number.content := date().day.toString
  number.font.size @= calendar.points
  number.color := (if (!allowed()) Theme.muted().withAlpha(0.6) else if (mark().selected) Theme.onAccent()
    else if (today()) Theme.accentText() else Theme.text())
  number.position.center := cellWidth / 2.0
  number.position.middle := cellHeight / 2.0

  children ++= List(band, circle, number)

  event.click.attach { e =>
    e.consume()
    calendar.pick(date())
  }
  event.pointer.enter.attach(_ => if (inMonth()) calendar.pointed @= Some(date()))
  event.pointer.leave.attach(_ => if (calendar.pointed().contains(date())) calendar.pointed @= None)
  event.key.down.attach(e => calendar.dayKey(e))
  focused.attach(_ => calendar.dayFocused())

  override def keepsEvent(e: KeyEvent): Boolean = calendar.keepsDay(e)

  override def kind: String = "CalendarDay(" + date().iso + ")"
}
