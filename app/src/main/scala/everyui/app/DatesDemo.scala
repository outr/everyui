package everyui.app

import everyui.component.types.FontWeight
import everyui.component.{Button, ButtonStyle, Column, Container, Row, ScrollContainer, Select, TextInput, TextView}
import everyui.date.{CalendarDate, DateField, DatePicker, DatePreset, DateRange, DateRangeField, DateRangePicker}
import everyui.dialog.{DialogAction, Dialogs}
import everyui.i18n.{Direction, Language, Strings}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** The date pickers: a date field and a calendar sharing one date, a range field and a range calendar with presets
  * sharing one range, buttons that ask for either in a dialog, and a form in a dialog (New task) whose date fields,
  * select and Delete open their own over it, which give it back as it was. `theme=light` shows the light palette,
  * `lang=<code>` a language's names and first day (`lang=ar` right to left), `today=<iso>` a fixed today, `min=`/`max=`
  * bounds, and `dialog=date`, `dialog=range` or `dialog=task` opens that dialog at once. Logs `dates ok` once built,
  * `dates chose <iso>` as a dialog answers, and `dates task <words> <iso>` as the form is done. */
object DatesDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    val code = Platform.launchArg("lang")
    if (code.nonEmpty) {
      Strings.language @= Language(code, code, code, rtl = code == "ar")
      Direction.set(code == "ar")
    }
    ui.backgroundColor := Theme.background()
    val today = CalendarDate.parse(Platform.launchArg("today")).getOrElse(CalendarDate.today())
    val min = CalendarDate.parse(Platform.launchArg("min"))
    val max = CalendarDate.parse(Platform.launchArg("max"))
    val date = Var(Option(today.plusDays(3)))
    val range = Var(Option(DateRange(today.plusDays(-6), today)))
    val presets = List(DatePreset.lastDays("Last 7 days", 7), DatePreset.lastDays("Last 30 days", 30),
      DatePreset.thisMonth("This month"), DatePreset.lastMonth("Last month"), DatePreset.yearToDate("This year"))
    val chosen = Var("")
    val wide = ui.size.width() >= 760.0
    val across = if (wide) 322.0 else math.min(322.0, ui.size.width() - 32.0)

    val dateField = new DateField("Due date", date, min = min, max = max, width = across)
    val picker = new DatePicker(date, min, max, today = today, width = across)
    val rangeField = new DateRangeField(range, min = min, max = max, presets = presets,
      width = if (wide) 420.0 else across)
    val rangePicker = new DateRangePicker(range, min, max, presets, today = today, width = across)
    val said = TextView.bound(date().map(_.iso).getOrElse("none") + "   " + range().map(_.iso).getOrElse("none"), 12.0,
      Theme.muted())

    val single = Column(14.0)(heading("A date"), dateField, picker)
    val double = Column(14.0)(heading("A range"), rangeField, rangePicker)
    val body = if (wide) Row(40.0)(single, double) else Column(28.0)(single, double)
    val bar = Row(10.0, middle = true)(
      new Button("Pick a date", () => askDate(date, min, max, today, chosen), style = ButtonStyle.Filled),
      new Button("Pick a range", () => askRange(range, min, max, presets, today, chosen)),
      new Button("New task", () => askTask(min, max, today, chosen)),
      new Button("Light / dark", () =>
        Theme.palette @= (if (Theme.palette() eq Palette.light) Palette.dark else Palette.light)))
    val answer = TextView.bound(chosen(), 12.0, Theme.muted())
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height() - 64.0
      position.top @= 64.0
    }
    val content = Column(12.0)(said, body)
    bar.position.left @= 16.0
    bar.position.top @= 12.0
    answer.position.left := bar.position.left() + bar.size.width() + 12.0
    answer.position.top @= 24.0
    content.position.left := (if (Direction.rtl()) ui.size.width() - content.size.width() - 16.0 else 16.0)
    content.position.top @= 8.0
    scroll.content.children += content
    ui.children ++= List(bar, answer, scroll)
    Platform.log("dates ok " + picker.size.width().toInt.toString + "x" + picker.size.height().toInt.toString)
    val open = Platform.launchArg("dialog")
    if (open == "date") Platform.after(0.2, () => askDate(date, min, max, today, chosen))
    if (open == "range") Platform.after(0.2, () => askRange(range, min, max, presets, today, chosen))
    if (open == "task") Platform.after(0.2, () => askTask(min, max, today, chosen))
    // a frame saved for a check (--freeze with --screenshot on the desktop), once a dialog is up
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
  }

  private def heading(words: String): Container = {
    val text = TextView(words, 18.0)
    text.font.weight @= FontWeight.SemiBold
    everyui.kit.Mirror.start(text, () => text.size.width())
  }

  private def askDate(date: Var[Option[CalendarDate]], min: Option[CalendarDate], max: Option[CalendarDate],
                      today: CalendarDate, chosen: Var[String]): Unit =
    DatePicker.pick(initial = date(), min = min, max = max, today = today).map { answer =>
      answer.foreach(d => date @= Some(d))
      chosen @= answer.map(d => "Chose " + d.iso).getOrElse("Cancelled")
      Platform.log("dates chose " + answer.map(_.iso).getOrElse("nothing"))
    }.start()

  private def askRange(range: Var[Option[DateRange]], min: Option[CalendarDate], max: Option[CalendarDate],
                       presets: List[DatePreset], today: CalendarDate, chosen: Var[String]): Unit =
    DateRangePicker.pick(initial = range(), min = min, max = max, presets = presets, today = today).map { answer =>
      answer.foreach(r => range @= Some(r))
      chosen @= answer.map(r => "Chose " + r.iso).getOrElse("Cancelled")
      Platform.log("dates chose " + answer.map(_.iso).getOrElse("nothing"))
    }.start()

  // a form in a dialog: its fields open their calendars over it, and Delete asks first, over it too, each giving it
  // back with what was typed
  private def askTask(min: Option[CalendarDate], max: Option[CalendarDate], today: CalendarDate,
                      chosen: Var[String]): Unit = {
    val title = new TextInput("Title", width = 320.0)
    val start = Var(Option(today))
    val last = Var(Option.empty[CalendarDate])
    val priority = new Select(List("Low", "Normal", "High"))
    priority.size.width @= 160.0
    priority.size.height @= 40.0
    priority.selected @= 1
    val delete = new Button("Delete", () => Dialogs.confirm("Delete this task?", "It cannot be undone.", "Delete",
      danger = true).map(yes => chosen @= (if (yes) "Deleted" else "Kept")).start())
    val body = List(title, new DateField("Date", start, min = min, max = max),
      new DateField("Last day", last, min = min, max = max), priority, delete)
    Dialogs.show("New task", body, close => List(DialogAction("Done", close, default = true))).map { _ =>
      chosen @= "Task: " + title.value() + " " + start().map(_.iso).getOrElse("none")
      Platform.log("dates task " + title.value() + " " + start().map(_.iso).getOrElse("none"))
    }.start()
  }
}
