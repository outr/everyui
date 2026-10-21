package everyui.date

import everyui.Color
import everyui.component.types.Semantics
import everyui.component.{Container, TextView}
import everyui.event.KeyEvent
import everyui.theme.Theme
import reactify.*

/** One year on a calendar's grid of years, the `index`th of the page: a pill, filled for the year shown, ringed for
  * this year and while it has the focus; muted and doing nothing outside the calendar's `min` and `max`. Only the year
  * the focus is on takes it, and the keys move it (`Calendar.yearKey`); a click, a tap or OK chooses it. */
class CalendarYear(calendar: Calendar, index: Int, cellWidth: Double, cellHeight: Double) extends Container {
  /** The year it shows. */
  val year: Val[Int] = own(Val(calendar.yearsFrom() + index))
  private val allowed: Val[Boolean] = own(Val(calendar.min.forall(_.year <= year()) && calendar.max.forall(_.year >= year())))
  private val current: Val[Boolean] = own(Val(year() == calendar.active().year))
  private val lit: Val[Boolean] = own(Val(allowed() && (focusShown() || event.pointer.over())))
  private val pill = new Container
  private val words = new TextView

  size.width @= cellWidth
  size.height @= cellHeight
  focusable := calendar.choosingYear() && year() == calendar.activeYear()
  disabled := !allowed()
  semantics := Semantics("gridcell", label = year().toString + (if (current()) ", " + DateWords.selected else ""))

  pill.size.width @= math.min(cellWidth - 8.0, calendar.points * 5.2)
  pill.size.height @= math.min(cellHeight - 8.0, calendar.points * 2.6)
  pill.radius := pill.size.height() / 2.0
  pill.position.left := (cellWidth - pill.size.width()) / 2.0
  pill.position.top := (cellHeight - pill.size.height()) / 2.0
  pill.backgroundColor := (if (current()) Theme.accent() else if (lit()) Theme.controlLit() else Color.Clear)
  pill.borderWidth := (if (focusShown()) 2.5 else if (year() == calendar.today.year) 1.0 else 0.0)
  pill.borderColor := (if (focusShown() && current()) Theme.text() else Theme.accent())

  words.content := year().toString
  words.font.size @= calendar.points
  words.color := (if (!allowed()) Theme.muted().withAlpha(0.6) else if (current()) Theme.onAccent() else Theme.text())
  words.position.center := cellWidth / 2.0
  words.position.middle := cellHeight / 2.0

  children ++= List(pill, words)

  event.click.attach { e =>
    e.consume()
    if (allowed()) calendar.pickYear(year())
  }
  event.key.down.attach(e => calendar.yearKey(e))

  override def keepsEvent(e: KeyEvent): Boolean = calendar.keepsYear(e)

  override def kind: String = "CalendarYear(" + year().toString + ")"
}
