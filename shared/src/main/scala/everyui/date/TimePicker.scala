package everyui.date

import everyui.async.Task
import everyui.component.types.FontWeight
import everyui.component.{Container, TextView}
import everyui.i18n.{DateText, Strings, Words}
import everyui.theme.Theme
import reactify.*

/** A time chooser, in shared EveryUI: it runs on every backend. Wheels for the hour and the minute (in `step`s), and,
  * where the language's clock runs to 12 hours (`DateText.twelveHour`), one for the morning or the afternoon, in that
  * order whatever the reading direction, as a time's digits are; above them the time chosen in words ("3:30 PM").
  * `value` is a `Var` the app gives and reads, in step both ways with the wheels: a wheel turned sets it, and a time
  * the app sets turns them. With nothing chosen they show `start` until one is turned.
  *
  * `TimePicker.pick` asks for a time in a dialog; `TimeField` is the field that does it.
  *
  * {{{
  *   val arrived = Var(Option.empty[ClockTime])
  *   val picker = new TimePicker(arrived, step = 15)
  * }}} */
class TimePicker(val value: Var[Option[ClockTime]] = Var(None), val step: Int = 5,
                 twelve: Boolean = DateText.twelveHour(), start: ClockTime = ClockTime(12, 0)) extends Container {
  private val every = math.max(1, math.min(30, step))
  private val minutes = 60 / every
  private def first: ClockTime = ClockTime.rounded(value().getOrElse(start), every)
  private val hourAt = Var(hourIndex(first))
  private val minuteAt = Var(first.minute / every)
  private val periodAt = Var(if (first.hour >= 12) 1 else 0)
  // the wheels are turned to `value` as it changes, without that turning setting it back
  private var following = false

  val hours: TimeWheel = new TimeWheel(DateWords.hour, if (twelve) 12 else 24,
    i => if (twelve) (if (i == 0) "12" else i.toString) else f2(i), hourAt)
  val minuteWheel: TimeWheel = new TimeWheel(DateWords.minute, minutes, i => f2(i * every), minuteAt)
  val period: Option[TimeWheel] = if (!twelve) None else {
    val symbols = DateText.symbols(Strings.language().code)
    Some(new TimeWheel(DateWords.period, 2, i => if (i == 0) symbols.am else symbols.pm, periodAt))
  }
  private val headline = new TextView {
    content := value().map(_.text()).getOrElse(DateWords.noTime)
    font.size @= 28.0
    font.weight @= FontWeight.Normal
    color := (if (value().isEmpty) Theme.muted() else Theme.text())
  }
  private val colon = TextView(":", 28.0, Theme.muted())

  private def f2(n: Int): String = if (n < 10) "0" + n.toString else n.toString
  // the hour wheel's place for `t`: 0 is 12 on a 12-hour wheel
  private def hourIndex(t: ClockTime): Int = if (twelve) t.hour % 12 else t.hour
  private def chosen: ClockTime = {
    val h = if (twelve) hourAt() % 12 + (if (periodAt() == 1) 12 else 0) else hourAt()
    ClockTime(h, minuteAt() * every)
  }

  private val wheels = List(hours, minuteWheel) ++ period.toList
  private val gap = 8.0
  hours.position.top @= 56.0
  colon.position.left := hours.size.width() + (gap * 2.0 - colon.size.width()) / 2.0
  colon.position.top := 56.0 + (hours.size.height() - colon.size.height()) / 2.0
  minuteWheel.position.left := hours.size.width() + gap * 2.0
  minuteWheel.position.top @= 56.0
  period.foreach { p =>
    p.position.left := minuteWheel.position.left() + minuteWheel.size.width() + gap * 2.0
    p.position.top @= 56.0
  }
  size.width := wheels.map(_.size.width()).sum + gap * 2.0 * (wheels.size - 1)
  size.height := 56.0 + hours.size.height()
  headline.position.left := (size.width() - headline.size.width()) / 2.0
  children ++= List(headline, hours, colon, minuteWheel) ++ period.toList

  List(hourAt, minuteAt, periodAt).foreach(_.attach(_ => if (!following) {
    val t = chosen
    if (!value().contains(t)) value @= Some(t)
  }))
  follow(value, (v: Option[ClockTime]) => v.foreach { t =>
    val r = ClockTime.rounded(t, every)
    following = true
    try {
      hourAt @= hourIndex(r)
      minuteAt @= r.minute / every
      periodAt @= (if (r.hour >= 12) 1 else 0)
    } finally following = false
  })

  override def kind: String = "TimePicker"
}

object TimePicker {
  /** A time asked for in a dialog (everyui.dialog): a TimePicker at `initial`, else at the time now (to the nearest
    * `step`), answered with the time chosen, or None for Cancel, Back, Escape or a click outside.
    *
    * {{{
    *   TimePicker.pick(DateWords.selectTime, stop.arrived(), step = 15).map(_.foreach(t => stop.arrived @= Some(t)))
    *     .start()
    * }}} */
  def pick(title: String = DateWords.selectTime, initial: Option[ClockTime] = None, step: Int = 5,
           okLabel: String = Words.ok, cancelLabel: String = Words.cancel): Task[Option[ClockTime]] = {
    val start = initial.getOrElse(ClockTime.rounded(ClockTime.now(), math.max(1, step)))
    val value: Var[Option[ClockTime]] = Var(Some(start))
    val picker = new TimePicker(value, step)
    DateDialog.ask[ClockTime](title, picker, picker.size.width(), () => Some(picker.hours), () => value(), okLabel,
      cancelLabel)
  }
}
