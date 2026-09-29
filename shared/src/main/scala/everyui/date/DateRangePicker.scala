package everyui.date

import everyui.async.Task
import everyui.component.types.FontWeight
import everyui.component.{Button, ButtonStyle, Component, Container, Modal, TextView}
import everyui.i18n.{DateText, Words, WeekStart}
import everyui.layout.{FlowLayout, VerticalLayout}
import everyui.theme.Theme
import reactify.*

/** A range of dates chosen on a calendar, in shared EveryUI: the first day chosen starts it and the next one (not
  * before it) ends it (`RangeChoice`), the days between drawn as a band. While the end is still to be chosen, the band
  * runs to the day under the pointer or with the focus, as a preview. `presets` (the app's: `DatePreset.lastDays`, …)
  * sit above the calendar as buttons that choose their range at once; with `headline`, the range in words heads it.
  *
  * `value` is the range once both ends are chosen (None while a start waits for its end), in step both ways with the
  * calendar; `choice` is the choosing as it stands, a start waiting for its end included.
  *
  * `DateRangePicker.pick` asks for a range in a dialog, and `DateRangePicker.below` in a panel dropped from a field;
  * `DateRangeField` is the pair of fields that does both.
  *
  * {{{
  *   val period = Var(Option(DateRange(today.plusDays(-6), today)))
  *   new DateRangePicker(period, max = Some(today), presets = List(DatePreset.lastDays("Last 7 days", 7),
  *     DatePreset.lastDays("Last 30 days", 30), DatePreset.thisMonth("This month")))
  * }}} */
class DateRangePicker(val value: Var[Option[DateRange]] = Var(None), min: Option[CalendarDate] = None,
                      max: Option[CalendarDate] = None, presets: List[DatePreset] = Nil,
                      firstDay: Int = WeekStart.current(), today: CalendarDate = CalendarDate.today(),
                      width: Double = Calendar.width, headline: Boolean = true,
                      points: Double = Calendar.textSize()) extends Container {
  /** The range as it is being chosen. */
  val choice: Var[RangeChoice] = own(Var(RangeChoice.of(value())))
  val calendar: Calendar = new Calendar((day, at) => choice().mark(day, at), day => chosen(day),
    value().map(_.end).getOrElse(today), min, max, firstDay, today, width, points)
  private val words = new TextView
  /** The presets' buttons, in their order. */
  val presetButtons: List[Button] = presets.map(p => new Button(p.label, () => use(p), ButtonStyle.Outlined,
    points = points * 0.9))

  layout @= Some(VerticalLayout(points * 0.6))
  size.width @= width
  if (headline) {
    words.content := DateRangePicker.said(choice())
    words.font.size @= points * 1.6
    words.font.weight @= FontWeight.Normal
    words.color := (if (choice().start.isEmpty) Theme.muted() else Theme.text())
    children += DatePicker.start(words, width)
  }
  if (presetButtons.nonEmpty) {
    val flow = new Container
    flow.size.width @= width
    flow.layout @= Some(FlowLayout(6.0, 6.0))
    flow.children ++= presetButtons
    children += flow
  }
  children += calendar
  follow(value, (v: Option[DateRange]) => if (choice().range != v) {
    choice @= RangeChoice.of(v)
    v.foreach(r => if (!r.end.sameMonth(calendar.active()) && !r.start.sameMonth(calendar.active())) calendar.show(r.end))
  })

  /** The range OK would answer with: both ends, or the start alone as a day's range while the end waits. */
  def answer: Option[DateRange] = choice().range.orElse(choice().start.map(s => DateRange(s, s)))

  private def chosen(day: CalendarDate): Unit = {
    choice @= choice().choose(day)
    // none while a start waits for its end
    if (value() != choice().range) value @= choice().range
  }

  private def use(preset: DatePreset): Unit = {
    val r = preset.range(today)
    val kept = DateRange.of(r.start.clamp(min, max), r.end.clamp(min, max))
    choice @= RangeChoice.of(Some(kept))
    calendar.show(kept.end)
    value @= Some(kept)
  }

  override def kind: String = "DateRangePicker"
}

object DateRangePicker {
  /** A range asked for in a dialog (everyui.dialog): a DateRangePicker starting at `initial`, answered with the range
    * chosen (a start without its end as that one day), or None for Cancel, Back, Escape or a click outside.
    *
    * {{{
    *   DateRangePicker.pick(DateWords.selectRange, filter.period(), max = Some(CalendarDate.today()),
    *     presets = List(DatePreset.lastDays("Last 7 days", 7))).map(_.foreach(r => filter.period @= Some(r))).start()
    * }}} */
  def pick(title: String = DateWords.selectRange, initial: Option[DateRange] = None, min: Option[CalendarDate] = None,
           max: Option[CalendarDate] = None, presets: List[DatePreset] = Nil, firstDay: Int = WeekStart.current(),
           today: CalendarDate = CalendarDate.today(), okLabel: String = Words.ok,
           cancelLabel: String = Words.cancel): Task[Option[DateRange]] = {
    val width = DateDialog.width()
    val picker = new DateRangePicker(Var(initial), min, max, presets, firstDay, today, width)
    DateDialog.ask[DateRange](title, picker, width, () => picker.calendar.dayOf(picker.calendar.active()),
      () => picker.answer, okLabel, cancelLabel)
  }

  /** A range calendar in a panel dropped from `anchor`, starting at `initial`: once both ends are chosen (or a preset)
    * it closes and hands the range to `chosen`; Back, Escape or a click outside closes it with nothing chosen. */
  def below(anchor: Component, initial: Option[DateRange], min: Option[CalendarDate] = None,
            max: Option[CalendarDate] = None, presets: List[DatePreset] = Nil, firstDay: Int = WeekStart.current(),
            today: CalendarDate = CalendarDate.today())(chosen: DateRange => Unit): Unit = {
    val value = Var(initial)
    val picker = new DateRangePicker(value, min, max, presets, firstDay, today, headline = true)
    val panel = DateDialog.drop(anchor, picker, () => picker.calendar.dayOf(picker.calendar.active()))
    value.attach(v => v.foreach { r =>
      Modal.remove(panel)
      chosen(r)
    })
  }

  /** A range in words, as a headline says it: "Sep 1 – Sep 29", "Start date – End date" for ends not yet chosen. */
  def said(choice: RangeChoice): String =
    choice.start.map(d => DateText.monthDay(d.month, d.day)).getOrElse(DateWords.start) + " – " +
      choice.end.map(d => DateText.monthDay(d.month, d.day)).getOrElse(DateWords.end)
}
