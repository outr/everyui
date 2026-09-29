package everyui.app

import everyui.component.types.FontWeight
import everyui.component.{Button, ButtonStyle, Column, Container, Row, ScrollContainer, TextView}
import everyui.date.{CalendarDate, DateField, DatePicker, DatePreset, DateRange, DateRangeField, DateRangePicker}
import everyui.i18n.{Direction, Language, Strings}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** The date pickers: a date field and a calendar sharing one date, a range field and a range calendar with presets
  * sharing one range, and buttons that ask for either in a dialog. `theme=light` shows the light palette, `lang=<code>`
  * a language's names and first day (`lang=ar` right to left), `today=<iso>` a fixed today, `min=`/`max=` bounds, and
  * `dialog=date` or `dialog=range` opens that dialog at once. Logs `dates ok` once built, and `dates chose <iso>` as a
  * dialog answers. */
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
}
