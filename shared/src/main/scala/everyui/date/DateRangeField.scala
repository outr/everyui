package everyui.date

import everyui.component.{Container, IconButton, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.WeekStart
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A range of dates in two fields, a start and an end (each a `DateField`), `width` wide in all, with a button beside
  * them that opens a range calendar with the app's `presets`: in a panel dropped from the fields where the screen is
  * 600 wide or more, else in a dialog (`DateRangePicker.pick`). `value` is the range, both ways: Some once
  * both fields hold a date and the end is not before the start (which the fields say under them otherwise), None while
  * either is empty.
  *
  * {{{
  *   val period = Var(Option.empty[DateRange])
  *   new DateRangeField(period, max = Some(CalendarDate.today()),
  *     presets = List(DatePreset.lastDays("Last 7 days", 7), DatePreset.lastMonth("Last month")))
  * }}} */
class DateRangeField(val value: Var[Option[DateRange]] = Var(None), startLabel: String = DateWords.start,
                     endLabel: String = DateWords.end, format: DateFormat = DateFormat.numeric,
                     min: Option[CalendarDate] = None, max: Option[CalendarDate] = None,
                     presets: List[DatePreset] = Nil, width: Double = 520.0, firstDay: Int = WeekStart.current(),
                     typing: Boolean = !Platform.remoteNavigation()) extends Container {
  private val half: Double = (width - 44.0 - 12.0 * 2.0) / 2.0
  val start: DateField = new DateField(startLabel, Var(value().map(_.start)), format, min, max, half, false, firstDay,
    typing)
  val end: DateField = new DateField(endLabel, Var(value().map(_.end)), format, min, max, half, false, firstDay,
    typing)
  /** What is wrong with the two together: an end before the start. */
  val error: Var[Option[String]] = own(Var(None))
  private val opener = new IconButton(MaterialIcon.DateRange, DateWords.openCalendar, () => open())
  private val line = new Container
  private val warning = TextView.line(error().getOrElse(""), 12.0, Theme.dangerText())

  layout @= Some(VerticalLayout(4.0))
  line.layout @= Some(HorizontalLayout(12.0))
  line.children ++= List(start, end, opener)
  // the button in the middle of the fields, not of their warnings
  opener.position.top := (56.0 - opener.size.height()) / 2.0
  children ++= List(line, warning)

  // what is typed into either field makes the range; what the range is set to is written into both
  start.edited.attach(_ => joined())
  end.edited.attach(_ => joined())
  follow(value, (v: Option[DateRange]) => {
    if (start.value() != v.map(_.start)) start.value @= v.map(_.start)
    if (end.value() != v.map(_.end)) end.value @= v.map(_.end)
    if (v.nonEmpty) error @= None
  })

  /** The range calendar, open on the range in the fields: dropped from them, or in a dialog. */
  def open(): Unit =
    if (typing && ui.size.width() >= 600.0)
      DateRangePicker.below(line, value(), min, max, presets, firstDay)(r => value @= Some(r))
    else DateRangePicker.pick(DateWords.selectRange, value(), min, max, presets, firstDay)
      .map(_.foreach(r => value @= Some(r))).start()

  // the two fields' dates as a range, where they make one
  private def together(): Option[DateRange] = (start.value(), end.value()) match {
    case (Some(s), Some(e)) if !e.isBefore(s) => Some(DateRange(s, e))
    case _ => None
  }

  private def joined(): Unit = {
    val backwards = (start.value(), end.value()) match {
      case (Some(s), Some(e)) => e.isBefore(s)
      case _ => false
    }
    error @= (if (backwards) Some(DateWords.endBeforeStart) else None)
    if (!backwards && value() != together()) value @= together()
  }

  override def kind: String = "DateRangeField"
}
