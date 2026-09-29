package everyui.date

import everyui.async.Task
import everyui.component.types.FontWeight
import everyui.component.{Component, Container, Modal, TextView}
import everyui.i18n.{DateText, Words, WeekStart}
import everyui.layout.VerticalLayout
import everyui.theme.Theme
import reactify.*

/** A date chooser, in shared EveryUI: it runs on every backend. A `Calendar` over the month of `value` (else of
  * `today`), with the date chosen filled, and above it, with `headline`, that date in words ("Tue, Sep 29"). `value` is
  * a `Var` the app gives and reads, in step both ways with the calendar: a click, a tap or OK on a day chooses it, and
  * a date the app sets shows its month. `min` and `max` bound what can be chosen.
  *
  * `DatePicker.pick` asks for a date in a dialog, and `DatePicker.below` in a panel dropped from a field; `DateField`
  * is the field that does both.
  *
  * {{{
  *   val due = Var(Option(CalendarDate(2026, 10, 1)))
  *   val picker = new DatePicker(due, min = Some(CalendarDate.today()))
  *   due.attach(d => task.due @= d)
  * }}} */
class DatePicker(val value: Var[Option[CalendarDate]] = Var(None), min: Option[CalendarDate] = None,
                 max: Option[CalendarDate] = None, firstDay: Int = WeekStart.current(),
                 today: CalendarDate = CalendarDate.today(), width: Double = Calendar.width, headline: Boolean = true,
                 points: Double = Calendar.textSize()) extends Container {
  val calendar: Calendar = new Calendar((day, _) => DayMark(selected = value().contains(day)),
    day => if (!value().contains(day)) value @= Some(day), value().getOrElse(today), min, max, firstDay, today, width,
    points)
  private val words = new TextView

  layout @= Some(VerticalLayout(points * 0.6))
  size.width @= width
  if (headline) {
    words.content := value().map(d => DateText.weekdayMonthDay(d.month, d.day, d.weekday)).getOrElse(DateWords.noDate)
    words.font.size @= points * 1.8
    words.font.weight @= FontWeight.Normal
    words.color := (if (value().isEmpty) Theme.muted() else Theme.text())
    children += DatePicker.start(words, width)
  }
  children += calendar
  follow(value, (v: Option[CalendarDate]) => v.foreach(d => if (!d.sameMonth(calendar.active())) calendar.show(d)))

  override def kind: String = "DatePicker"
}

object DatePicker {
  /** A date asked for in a dialog (everyui.dialog): a DatePicker starting at `initial`, answered with the date chosen
    * (`initial` if none was chosen), or None for Cancel, Back, Escape or a click outside.
    *
    * {{{
    *   DatePicker.pick(DateWords.selectDate, Some(order.due)).map(_.foreach(d => order.due @= d)).start()
    * }}} */
  def pick(title: String = DateWords.selectDate, initial: Option[CalendarDate] = None, min: Option[CalendarDate] = None,
           max: Option[CalendarDate] = None, firstDay: Int = WeekStart.current(),
           today: CalendarDate = CalendarDate.today(), okLabel: String = Words.ok,
           cancelLabel: String = Words.cancel): Task[Option[CalendarDate]] = {
    val value = Var(initial)
    val width = DateDialog.width()
    val picker = new DatePicker(value, min, max, firstDay, today, width)
    DateDialog.ask[CalendarDate](title, picker, width, () => picker.calendar.dayOf(picker.calendar.active()),
      () => value(), okLabel, cancelLabel)
  }

  /** A calendar in a panel dropped from `anchor` (a field), starting at `initial`: a day chosen closes it and is handed
    * to `chosen`; Back, Escape or a click outside closes it with nothing chosen. */
  def below(anchor: Component, initial: Option[CalendarDate], min: Option[CalendarDate] = None,
            max: Option[CalendarDate] = None, firstDay: Int = WeekStart.current(),
            today: CalendarDate = CalendarDate.today())(chosen: CalendarDate => Unit): Unit = {
    val value = Var(initial)
    val picker = new DatePicker(value, min, max, firstDay, today, headline = false)
    val panel = DateDialog.drop(anchor, picker, () => picker.calendar.dayOf(picker.calendar.active()))
    value.attach(v => v.foreach { d =>
      Modal.remove(panel)
      chosen(d)
    })
  }

  /** `c` at the start of a strip `width` wide (everyui.kit.Mirror.start). */
  private[date] def start(c: Component, width: Double): Container = everyui.kit.Mirror.start(c, () => width)
}
