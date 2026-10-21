package everyui.date

import everyui.component.types.Semantics
import everyui.component.{Container, IconButton, TextInput, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.{Strings, WeekStart}
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A date in a field, `width` wide: the date written as `format` writes it (in numbers, as the language writes them,
  * unless it says), what is typed read back (`DateFormat.read`, which takes the other ways of writing it too), and a
  * button beside it that opens a calendar: in a panel dropped from the field where the screen is 600 wide or more,
  * else in a dialog (`DatePicker.pick`). `value` is the date, both ways; the field emptied is None. What is
  * typed changes `value` only once it is a date inside `min` and `max`, and a field left holding anything else says
  * so under it (`error`); left holding a date, it is written again as `format` writes it.
  *
  * Where the app is worked by a remote (`Platform.remoteNavigation`, or `typing = false`), there is nothing to type:
  * the field shows the date, and OK on it opens the calendar in a dialog.
  *
  * {{{
  *   val due = Var(Option.empty[CalendarDate])
  *   val field = new DateField("Due", due, min = Some(CalendarDate.today()))
  *   new DateField("Born", born, DateFormat.medium, max = Some(CalendarDate.today()))
  * }}} */
class DateField(label: String = DateWords.date, val value: Var[Option[CalendarDate]] = Var(None),
                format: DateFormat = DateFormat.numeric, min: Option[CalendarDate] = None,
                max: Option[CalendarDate] = None, width: Double = 320.0, button: Boolean = true,
                firstDay: Int = WeekStart.current(), typing: Boolean = !Platform.remoteNavigation()) extends Container {
  /** What is wrong with what the field holds, if anything: not a date, or one out of range. */
  val error: Var[Option[String]] = own(Var(None))
  /** A date typed in, or the field emptied, as it changes `value`: what was typed, apart from what the app set. */
  val edited: Channel[Option[CalendarDate]] = Channel[Option[CalendarDate]]
  private val across: Double = if (button) width - 44.0 else width
  /** The field typed into, where there is typing. */
  val input: Option[TextInput] = if (typing) Some(new TextInput(label, width = across)) else None
  private val face = new Container
  private val opener = new IconButton(MaterialIcon.CalendarMonth, DateWords.openCalendar, () => open())
  private val line = new Container
  private val warning = TextView.line(if (input.exists(_.editing())) "" else error().getOrElse(""), 12.0, Theme.danger())

  layout @= Some(VerticalLayout(4.0))
  line.layout @= Some(HorizontalLayout(12.0, middle = true))
  input match {
    case Some(field) =>
      field.value @= value().map(d => format.write(d)).getOrElse("")
      field.value.attach(t => read(t))
      field.editing.attach(e => if (!e) tidy())
      field.submitted.attach(_ => tidy())
      line.children += field
    case None =>
      // what a remote works: the date shown as a field shows it, and OK opens the calendar
      val caption = TextView(label, 12.0, Theme.muted())
      val shown = TextView.bound(value().map(d => format.write(d)).getOrElse(""), 16.0)
      face.focusable @= true
      face.size.width @= across
      face.size.height @= 56.0
      face.radius @= 10.0
      face.borderWidth := (if (face.focusShown()) 2.5 else 1.0)
      face.borderColor := (if (face.focusShown()) Theme.accent() else Theme.border())
      face.backgroundColor := (if (face.focusShown()) Theme.controlLit() else Theme.raised())
      face.semantics := Semantics.button(label + ", " + value().map(d => format.write(d)).getOrElse(""))
      caption.position.left @= 14.0
      caption.position.top @= 7.0
      shown.position.left @= 14.0
      shown.position.top @= 26.0
      face.children ++= List(caption, shown)
      face.event.click.attach { e =>
        e.consume()
        open()
      }
      line.children += face
  }
  if (button) line.children += opener
  children ++= List(line, warning)

  follow(value, (v: Option[CalendarDate]) => input.foreach { field =>
    if (read(field.value(), false) != v) {
      field.value @= v.map(d => format.write(d)).getOrElse("")
      error @= None
    }
  })
  // written again in the language it changes to
  follow(Strings.language, (_: everyui.i18n.Language) => if (error().isEmpty) tidy())

  /** The calendar, open on the date in the field: dropped from it, or in a dialog. */
  def open(): Unit =
    if (typing && ui.size.width() >= 600.0)
      DatePicker.below(line, value(), min, max, firstDay)(d => set(d))
    else DatePicker.pick(label, value(), min, max, firstDay).map(_.foreach(d => set(d))).start()

  private def set(d: CalendarDate): Unit = {
    error @= None
    if (value().contains(d)) tidy()
    else {
      value @= Some(d)
      edited @= Some(d)
    }
  }

  /** What `text` means: a date inside the range, or None; with `update`, `value` and `error` follow it. */
  private def read(text: String, update: Boolean = true): Option[CalendarDate] =
    if (text.trim.isEmpty) {
      if (update) {
        error @= None
        if (value().nonEmpty) {
          value @= None
          edited @= None
        }
      }
      None
    }
    else format.read(text) match {
      case Some(d) if d.within(min, max) =>
        if (update) {
          error @= None
          if (!value().contains(d)) {
            value @= Some(d)
            edited @= Some(d)
          }
        }
        Some(d)
      case Some(_) =>
        if (update) error @= Some(DateWords.outOfRange)
        None
      case None =>
        if (update) error @= Some(DateWords.invalid)
        None
    }

  // a date the field holds, written as the format writes it
  private def tidy(): Unit = input.foreach { field =>
    if (error().isEmpty) {
      val written = value().map(d => format.write(d)).getOrElse("")
      if (field.value() != written) field.value @= written
    }
  }

  override def kind: String = "DateField(" + label + ")"
}
