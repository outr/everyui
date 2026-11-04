package everyui.date

import everyui.component.{Component, Container, Modal}
import everyui.dialog.DialogButton
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The pickers on the desktop's real nodes, the keys sent to the app's own scene: the keyboard across days, weeks,
  * months and years, a range chosen with its preview, the bounds, the grid of years, a field typed into, a range field
  * in step with a range picker, the dialog, the labels a screen reader hears, and a right-to-left calendar. */
class DatePickerSpec extends FxSpec {
  private val today = CalendarDate(2026, 9, 29)
  private def day(m: Int, d: Int, y: Int = 2026): CalendarDate = CalendarDate(y, m, d)

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      c.position.left @= 100.0
      c.position.top @= 50.0
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def press(code: KeyCode, shift: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, c.absoluteLeft + 5.0,
    c.absoluteTop + 5.0))

  private def cell(calendar: Calendar, date: CalendarDate): CalendarDay = fx(dayCell(calendar, date))
  private def dayCell(calendar: Calendar, date: CalendarDate): CalendarDay = calendar.dayOf(date).get

  private def focusedDate: Option[CalendarDate] = fx(Focus.focused().flatMap(c => c match {
    case d: CalendarDay => Some(d.date())
    case _ => None
  }))

  "A date picker" should {
    "move the focus by day, week, month and year from the keyboard, and choose with Enter" in {
      val value = Var(Option(day(9, 29)))
      val picker = fx(new DatePicker(value, firstDay = 7, today = today))
      val calendar = picker.calendar
      onScreen(picker) {
        fx(calendar.focusBody())
        focusedDate shouldBe Some(day(9, 29))
        press(KeyCode.RIGHT)
        focusedDate shouldBe Some(day(9, 30))
        press(KeyCode.RIGHT)
        focusedDate shouldBe Some(day(10, 1))
        fx(calendar.shown()) shouldBe day(10, 1)
        press(KeyCode.UP)
        focusedDate shouldBe Some(day(9, 24))
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        focusedDate shouldBe Some(day(10, 8))
        press(KeyCode.PAGE_DOWN)
        focusedDate shouldBe Some(day(11, 8))
        press(KeyCode.PAGE_UP, shift = true)
        focusedDate shouldBe Some(day(11, 8, 2025))
        press(KeyCode.HOME)
        focusedDate shouldBe Some(day(11, 2, 2025))     // a week from Sunday
        press(KeyCode.END)
        focusedDate shouldBe Some(day(11, 8, 2025))
        fx(value()) shouldBe Some(day(9, 29))
        press(KeyCode.ENTER)
        fx(value()) shouldBe Some(day(11, 8, 2025))
        // only the day with the focus takes it: the grid is one stop for Tab
        fx(Focus.candidates(calendar.days.head.parent().get.parent().get).size) shouldBe 1
      }
    }
    "choose a day clicked, show the month of a date the app sets, and mark today" in {
      val value = Var(Option.empty[CalendarDate])
      val picker = fx(new DatePicker(value, today = today))
      val calendar = picker.calendar
      onScreen(picker) {
        fx(calendar.shown()) shouldBe day(9, 1)
        click(cell(calendar, day(9, 12)))
        fx(value()) shouldBe Some(day(9, 12))
        fx(value @= Some(day(2, 29, 2028)))
        fx(calendar.shown()) shouldBe day(2, 1, 2028)
        fx(dayCell(calendar, day(2, 29, 2028)).semantics().label) shouldBe "Tuesday, February 29, 2028, selected"
        fx(value @= Some(day(9, 3)))
        fx(dayCell(calendar, today).semantics()) shouldBe
          everyui.component.types.Semantics("gridcell", label = "Tuesday, September 29, 2026, Today")
      }
    }
    "keep to its bounds: days outside them muted, not chosen and not reached" in {
      val value = Var(Option(day(9, 10)))
      val picker = fx(new DatePicker(value, min = Some(day(9, 5)), max = Some(day(10, 3)), today = today))
      val calendar = picker.calendar
      onScreen(picker) {
        click(cell(calendar, day(9, 2)))
        fx(value()) shouldBe Some(day(9, 10))
        fx(dayCell(calendar, day(9, 2)).disabled()) shouldBe true
        fx(calendar.focusBody())
        press(KeyCode.UP)
        focusedDate shouldBe Some(day(9, 5))
        press(KeyCode.PAGE_DOWN)
        focusedDate shouldBe Some(day(10, 3))
        press(KeyCode.RIGHT)
        focusedDate shouldBe Some(day(10, 3))
        fx(calendar.page(1))
        fx(calendar.shown()) shouldBe day(10, 1)
      }
    }
    "jump to a year on the grid of years" in {
      val value = Var(Option(day(9, 29)))
      val picker = fx(new DatePicker(value, today = today))
      val calendar = picker.calendar
      onScreen(picker) {
        fx(calendar.choosingYear @= true)
        fx(calendar.years.map(_.year()).take(2)) shouldBe List(2020, 2021)
        val y = fx(calendar.years.find(_.focusable()).get)
        fx(y.year()) shouldBe 2026
        fx(calendar.focusBody())
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        fx(calendar.activeYear()) shouldBe 2038
        press(KeyCode.DOWN)
        fx(calendar.activeYear()) shouldBe 2042
        fx(calendar.yearsFrom()) shouldBe 2040
        press(KeyCode.ENTER)
        fx(calendar.choosingYear()) shouldBe false
        fx(calendar.shown()) shouldBe day(9, 1, 2042)
        focusedDate shouldBe Some(day(9, 29, 2042))
      }
    }
    "hand the focus between the header and the grid on a remote, and out of the calendar below it" in {
      val was = fx(everyui.render.Host.args)
      fx(everyui.render.Host.args = was + ("remote" -> "true"))
      try {
        val value = Var(Option(day(9, 29)))
        val picker = fx(new DatePicker(value, firstDay = 7, today = today))
        val below = fx(new everyui.component.Button("Below"))
        val column = fx(everyui.component.Column(8.0)(picker, below))
        onScreen(column) {
          fx(picker.calendar.focusBody())
          press(KeyCode.UP)
          focusedDate shouldBe Some(day(9, 22))
          press(KeyCode.UP)
          press(KeyCode.UP)
          press(KeyCode.UP)
          focusedDate shouldBe Some(day(9, 1))
          press(KeyCode.UP)                 // out of the month: up to the header, on the month's name
          fx(Focus.focused().exists(_.kind == "Container")) shouldBe true
          focusedDate shouldBe None
          press(KeyCode.RIGHT)
          press(KeyCode.DOWN)               // down from the header into the grid, on the day with the focus
          focusedDate shouldBe Some(day(9, 1))
          press(KeyCode.LEFT)               // Left and Right still turn the month
          focusedDate shouldBe Some(day(8, 31))
          press(KeyCode.RIGHT)
          press(KeyCode.DOWN)
          press(KeyCode.DOWN)
          press(KeyCode.DOWN)
          press(KeyCode.DOWN)
          focusedDate shouldBe Some(day(9, 29))
          press(KeyCode.DOWN)               // out of the month: on to what is below the calendar
          fx(Focus.focused().exists(_ eq below)) shouldBe true
        }
      } finally fx(everyui.render.Host.args = was)
    }
    "mirror Left and Right in a right-to-left language" in {
      fx(Direction.set(true))
      try {
        val picker = fx(new DatePicker(Var(Option(day(9, 29))), today = today))
        onScreen(picker) {
          fx(picker.calendar.focusBody())
          press(KeyCode.LEFT)
          focusedDate shouldBe Some(day(9, 30))
          press(KeyCode.RIGHT)
          press(KeyCode.RIGHT)
          focusedDate shouldBe Some(day(9, 28))
          // the week reads from the right: its first day right of its last
          val row = fx(picker.calendar.days.slice(28, 35))
          fx(row.head.position.left() > row.last.position.left()) shouldBe true
        }
      } finally fx(Direction.set(false))
    }
    "answer from its dialog with the day chosen, or None when cancelled" in {
      var answer: Option[Option[CalendarDate]] = None
      fx(DatePicker.pick("Due", Some(day(9, 29)), today = today).map(a => answer = Some(a)).start())
      focusedDate shouldBe Some(day(9, 29))
      press(KeyCode.RIGHT)
      press(KeyCode.ENTER)
      val buttons = fx(Focus.candidates(ui).collect { case b: DialogButton => b }.toList)
      buttons.size shouldBe 2
      click(buttons(1))
      eventually(answer.nonEmpty) shouldBe true
      answer shouldBe Some(Some(day(9, 30)))
      answer = None
      fx(DatePicker.pick("Due", None, today = today).map(a => answer = Some(a)).start())
      press(KeyCode.ESCAPE)
      eventually(answer.nonEmpty) shouldBe true
      answer shouldBe Some(None)
    }
  }

  "A range picker" should {
    "take a start, preview the range to the day pointed at, and take the end" in {
      val value = Var(Option.empty[DateRange])
      val picker = fx(new DateRangePicker(value, today = today))
      val calendar = picker.calendar
      onScreen(picker) {
        click(cell(calendar, day(9, 3)))
        fx(picker.choice()) shouldBe RangeChoice(Some(day(9, 3)), None)
        fx(calendar.pointed @= Some(day(9, 6)))
        fx(calendar.mark(day(9, 5))) shouldBe DayMark(before = true, after = true, preview = true)
        fx(calendar.pointed @= None)
        // the focus is a preview too: on the start, and moved on with the keys
        press(KeyCode.RIGHT)
        press(KeyCode.RIGHT)
        fx(calendar.mark(day(9, 4))) shouldBe DayMark(before = true, after = true, preview = true)
        press(KeyCode.ENTER)
        fx(value()) shouldBe Some(DateRange(day(9, 3), day(9, 5)))
        fx(calendar.mark(day(9, 4))) shouldBe DayMark(before = true, after = true)
        fx(picker.answer) shouldBe Some(DateRange(day(9, 3), day(9, 5)))
      }
    }
    "choose a preset at once" in {
      val value = Var(Option.empty[DateRange])
      val picker = fx(new DateRangePicker(value, presets = List(DatePreset.lastMonth("Last month")), today = today))
      onScreen(picker) {
        click(picker.presetButtons.head)
        fx(value()) shouldBe Some(DateRange(day(8, 1), day(8, 31)))
        fx(picker.calendar.shown()) shouldBe day(8, 1)
      }
    }
    "stay in step with a range field" in {
      val value = Var(Option(DateRange(day(9, 23), day(9, 29))))
      val field = fx(new DateRangeField(value))
      val picker = fx(new DateRangePicker(value, today = today))
      val both = fx {
        val c = new Container
        field.position.top @= 0.0
        picker.position.top @= 100.0
        c.children ++= List(field, picker)
        c
      }
      onScreen(both) {
        fx((field.start.input.get.value(), field.end.input.get.value())) shouldBe ("9/23/2026", "9/29/2026")
        click(cell(picker.calendar, day(9, 3)))
        fx(value()) shouldBe None
        click(cell(picker.calendar, day(9, 10)))
        fx(value()) shouldBe Some(DateRange(day(9, 3), day(9, 10)))
        fx((field.start.input.get.value(), field.end.input.get.value())) shouldBe ("9/3/2026", "9/10/2026")
        fx(field.end.input.get.value @= "9/1/2026")
        fx(field.error()) shouldBe Some(DateWords.endBeforeStart)
        fx(value()) shouldBe Some(DateRange(day(9, 3), day(9, 10)))
        fx(field.end.input.get.value @= "9/12/2026")
        fx(field.error()) shouldBe None
        fx(value()) shouldBe Some(DateRange(day(9, 3), day(9, 12)))
        fx(picker.calendar.mark(day(9, 11))) shouldBe DayMark(before = true, after = true)
      }
    }
  }

  "A date field" should {
    "read what is typed, say what is wrong, and write the date again as it is left" in {
      val value = Var(Option(day(9, 29)))
      val field = fx(new DateField("Due", value, min = Some(day(1, 1)), max = Some(day(12, 31))))
      val input = field.input.get
      onScreen(field) {
        fx(input.value()) shouldBe "9/29/2026"
        fx(input.editing @= true)
        fx(input.value @= "10/3/26")
        fx(value()) shouldBe Some(day(10, 3))
        fx(input.value @= "Oct 5 2026")
        fx(value()) shouldBe Some(day(10, 5))
        fx(input.editing @= false)
        fx(input.value()) shouldBe "10/5/2026"
        fx(input.value @= "13/40/2026")
        fx(field.error()) shouldBe Some(DateWords.invalid)
        fx(value()) shouldBe Some(day(10, 5))
        fx(input.value @= "1/1/2030")
        fx(field.error()) shouldBe Some(DateWords.outOfRange)
        fx(input.value @= "")
        fx(field.error()) shouldBe None
        fx(value()) shouldBe None
        fx(value @= Some(day(12, 25)))
        fx(input.value()) shouldBe "12/25/2026"
      }
    }
    "open its calendar from a press on the field, the caret left there to type on" in {
      val value = Var(Option(day(9, 29)))
      val field = fx(new DateField("Due", value))
      val input = field.input.get
      onScreen(field) {
        fx(ui.size.width()) should be >= 600.0
        fx(input.event.pointer.down @= PointerEvent(PointerKind.Down, input.absoluteLeft + 5.0,
          input.absoluteTop + 5.0))
        fx(field.isDropped) shouldBe true
        fx(Modal.showing) shouldBe false
        fx(input.editing @= true)
        fx(field.isDropped) shouldBe true
        // a date typed shows in the calendar, chosen there, and the calendar stays down
        fx(input.value @= "10/3/26")
        fx(value()) shouldBe Some(day(10, 3))
        val picker = fx(field.droppedPicker.get)
        fx(picker.calendar.active()) shouldBe day(10, 3)
        fx(picker.value()) shouldBe Some(day(10, 3))
        fx(field.isDropped) shouldBe true
        // a day chosen fills the field and puts the calendar away
        click(cell(picker.calendar, day(10, 20)))
        fx(value()) shouldBe Some(day(10, 20))
        fx(input.value()) shouldBe "10/20/2026"
        fx(field.isDropped) shouldBe false
        fx(ui.children().exists(_.isInstanceOf[Container] && Focus.within(picker, ui))) shouldBe false
      }
    }
    "put the calendar a press dropped away with Escape or a press outside, and open it from Alt+Down" in {
      val value = Var(Option(day(9, 29)))
      val field = fx(new DateField("Due", value))
      val input = field.input.get
      onScreen(field) {
        def drop(): Unit = fx(input.event.pointer.down @= PointerEvent(PointerKind.Down, input.absoluteLeft + 5.0,
          input.absoluteTop + 5.0))
        drop()
        fx(field.isDropped) shouldBe true
        fx(input.focus())
        press(KeyCode.ESCAPE)
        fx(field.isDropped) shouldBe false
        drop()
        fx(ui.event.pointer.down @= PointerEvent(PointerKind.Down, 2.0, 2.0))
        fx(field.isDropped) shouldBe false
        // a second press on the field while it is down leaves it down
        drop()
        drop()
        fx(field.isDropped) shouldBe true
        fx(field.close())
        // Alt+Down from the keys: the calendar with the focus in it, as the button opens it
        fx(input.event.key.down @= everyui.event.KeyEvent(everyui.event.Key.ArrowDown, "Down", alt = true))
        fx(Modal.showing) shouldBe true
        fx(Modal.close())
        fx(value()) shouldBe Some(day(9, 29))
      }
    }
    "write in the format it is given" in {
      val value = Var(Option(day(9, 29)))
      val field = fx(new DateField("Due", value, DateFormat.pattern("dd.MM.yyyy")))
      onScreen(field) {
        fx(field.input.get.value()) shouldBe "29.09.2026"
        fx(field.input.get.value @= "3.4.2026")
        fx(value()) shouldBe Some(day(4, 3))
      }
    }
  }
}
