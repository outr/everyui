package everyui.date

import everyui.component.types.Semantics
import everyui.component.{Component, Container, IconButton, Modal, TextInput, TextView}
import everyui.event.Key
import everyui.focus.Focus
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
  * A press on the field opens the calendar too, as most date inputs do: dropped under it, the caret left in the field
  * to type on (a date typed moves the calendar to it), put away by Escape, a press anywhere else, the focus moving on,
  * or a day chosen, which fills the field; where there is no room to drop it, the press opens the dialog. From the keys
  * the focus coming in by Tab opens nothing, and Alt+Down opens it as the button does, the focus in it.
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
  val input: Option[TextInput] = if (typing) Some(new TextInput(label, width = across) {
    // Alt+Down opens the calendar, as it does a select's list
    override def keepsEvent(e: everyui.event.KeyEvent): Boolean =
      (e.key == Key.ArrowDown && e.alt) || super.keepsEvent(e)
  }) else None
  private val face = new Container
  private val opener = new IconButton(MaterialIcon.CalendarMonth, DateWords.openCalendar, () => open())
  private val line = new Container
  private val warning = TextView.line(if (input.exists(_.editing())) "" else error().getOrElse(""), 12.0, Theme.dangerText())

  layout @= Some(VerticalLayout(4.0))
  line.layout @= Some(HorizontalLayout(12.0, middle = true))
  input match {
    case Some(field) =>
      field.value @= value().map(d => format.write(d)).getOrElse("")
      field.value.attach { t =>
        read(t)
        // a date typed while the calendar is down shows there
        if (dropped.nonEmpty) read(t, update = false).foreach(d => showing(d))
      }
      field.editing.attach(e => if (!e) tidy())
      field.submitted.attach(_ => tidy())
      // a press on the field opens the calendar as well as putting the caret in: dropped under it where there is room,
      // the focus left in the field to type on; a dialog where there is not
      field.event.pointer.down.attach(_ => if (dropped.isEmpty) Platform.later(() => pressed()))
      field.event.key.down.attach(k => if (k.key == Key.ArrowDown && k.alt) open())
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
      face.borderColor := (if (face.focusShown()) Theme.accent() else Theme.edge())
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
  onDispose(() => close())

  follow(value, (v: Option[CalendarDate]) => input.foreach { field =>
    if (read(field.value(), false) != v) {
      field.value @= v.map(d => format.write(d)).getOrElse("")
      error @= None
    }
  })
  // written again in the language it changes to
  follow(Strings.language, (_: everyui.i18n.Language) => if (error().isEmpty) tidy())

  /** The calendar, open on the date in the field, the focus in it: dropped from it, or in a dialog. What the button,
    * Alt+Down and a remote's OK open. */
  def open(): Unit = {
    close()
    if (typing && ui.size.width() >= 600.0)
      DatePicker.below(line, value(), min, max, firstDay)(d => set(d))
    else DatePicker.pick(label, value(), min, max, firstDay).map(_.foreach(d => set(d))).start()
  }

  /** The calendar a press on the field dropped, while it is down: its panel, and what puts it away. */
  private var dropped: Option[(Container, DatePicker)] = None
  private var undrop: List[() => Unit] = Nil

  // a press on the field: the calendar dropped beside the caret where there is room, else the dialog
  private def pressed(): Unit =
    if (!(typing && ui.size.width() >= 600.0)) open()
    else if (dropped.isEmpty && !Modal.showing) {
      // the date now, not a Var bound to `value`: the picker's choice is its own until a day is chosen
      val now = value()
      val picker = new DatePicker(Var(now), min, max, firstDay, CalendarDate.today(), headline = false)
      val panel = DateDialog.panel(picker)
      DateDialog.place(panel, line)
      ui.children += panel
      dropped = Some(panel -> picker)
      picker.value.attach(v => if (typed.nonEmpty && typed == v) typed = None else v.foreach { d =>
        close()
        set(d)
        input.foreach(_.focus())
      })
      // Escape (or Back) puts it away, before anything else hears it
      val unback = Focus.onBack(_ => {
        val was = dropped.nonEmpty
        close()
        was
      })
      // a press anywhere but on it or the field puts it away
      val pressing = ui.event.pointer.down.attach { e =>
        if (!inside(panel, e.x, e.y) && !inside(line, e.x, e.y)) close()
      }
      // the focus gone somewhere else, by Tab or by the app
      val leaving = Focus.focused.attach { f =>
        if (f.exists(c => !Focus.within(c, panel) && !Focus.within(c, line))) close()
      }
      undrop = List(unback, () => ui.event.pointer.down.reactions -= pressing, () => Focus.focused.reactions -= leaving)
    }

  /** The calendar a press dropped put away, where it is down. */
  def close(): Unit = dropped.foreach { case (panel, _) =>
    dropped = None
    val stops = undrop
    undrop = Nil
    stops.foreach(f => f())
    ui.children -= panel
    panel.dispose()
  }

  /** Whether a calendar is down under the field now (dropped by a press). */
  def isDropped: Boolean = dropped.nonEmpty

  // the calendar down now, for a spec to work
  private[date] def droppedPicker: Option[DatePicker] = dropped.map(_._2)

  // the date typed that the calendar is being moved to: heard back from it as typed, not as a day chosen there (heard
  // later where reactify puts a change made inside another's reaction after it)
  private var typed: Option[CalendarDate] = None

  // the dropped calendar moved to `d`, chosen there, without choosing it
  private def showing(d: CalendarDate): Unit = dropped.foreach { case (_, picker) =>
    if (!picker.value().contains(d)) {
      typed = Some(d)
      picker.value @= Some(d)
    }
    picker.calendar.show(d)
  }

  private def inside(c: Component, x: Double, y: Double): Boolean =
    x >= c.absoluteLeft && x < c.absoluteLeft + c.size.width() &&
      y >= c.absoluteTop && y < c.absoluteTop + c.size.height()

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
