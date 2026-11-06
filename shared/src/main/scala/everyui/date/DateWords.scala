package everyui.date

import everyui.i18n.Words

/** What the date pickers say, in the language in use (Words' eleven languages): an app gives its own wording or
  * another language with `Words.add` under these keys ("date.selectDate", …). The month and weekday names are
  * `everyui.i18n.DateText`'s. */
object DateWords {
  private def word(key: String): String = Words(key)

  def selectDate: String = word("date.selectDate")
  def selectRange: String = word("date.selectRange")
  def date: String = word("date.date")
  def start: String = word("date.start")
  def end: String = word("date.end")
  def previousMonth: String = word("date.previousMonth")
  def nextMonth: String = word("date.nextMonth")
  def previousYears: String = word("date.previousYears")
  def nextYears: String = word("date.nextYears")
  def previousYear: String = word("date.previousYear")
  def nextYear: String = word("date.nextYear")
  def chooseYear: String = word("date.chooseYear")
  def chooseMonth: String = word("date.chooseMonth")
  def openCalendar: String = word("date.openCalendar")
  def today: String = word("date.today")
  def selected: String = word("date.selected")
  /** What a date field says when what is in it is not a date. */
  def invalid: String = word("date.invalid")
  /** What a date field says of a date outside the ones allowed. */
  def outOfRange: String = word("date.outOfRange")
  /** What a range field says of an end before its start. */
  def endBeforeStart: String = word("date.endBeforeStart")
  /** A date picker's headline with nothing chosen. */
  def noDate: String = word("date.noDate")
  /** What a time field is called, and a time picker's title (`TimeField`, `TimePicker`). */
  def time: String = word("date.time")
  def selectTime: String = word("date.selectTime")
  /** A time picker's columns, to a screen reader. */
  def hour: String = word("date.hour")
  def minute: String = word("date.minute")
  def period: String = word("date.period")
  /** A time field's button that opens the picker. */
  def openClock: String = word("date.openClock")
  /** What a time field says when what is in it is not a time. */
  def invalidTime: String = word("date.invalidTime")
  /** A time picker's headline with nothing chosen. */
  def noTime: String = word("date.noTime")
}
