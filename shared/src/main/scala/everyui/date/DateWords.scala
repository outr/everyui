package everyui.date

import everyui.i18n.Words

/** What the date pickers say, in the language in use: an app gives its translations with `Words.add` under these keys
  * ("date.selectDate", …), and English is said where none is given. The month and weekday names are
  * `everyui.i18n.DateText`'s. */
object DateWords {
  private def word(key: String, english: String): String = {
    val said = Words(key)
    if (said == key) english else said
  }

  def selectDate: String = word("date.selectDate", "Select date")
  def selectRange: String = word("date.selectRange", "Select range")
  def date: String = word("date.date", "Date")
  def start: String = word("date.start", "Start date")
  def end: String = word("date.end", "End date")
  def previousMonth: String = word("date.previousMonth", "Previous month")
  def nextMonth: String = word("date.nextMonth", "Next month")
  def previousYears: String = word("date.previousYears", "Earlier years")
  def nextYears: String = word("date.nextYears", "Later years")
  def previousYear: String = word("date.previousYear", "Previous year")
  def nextYear: String = word("date.nextYear", "Next year")
  def chooseYear: String = word("date.chooseYear", "Choose a year")
  def chooseMonth: String = word("date.chooseMonth", "Choose a month")
  def openCalendar: String = word("date.openCalendar", "Open calendar")
  def today: String = word("date.today", "Today")
  def selected: String = word("date.selected", "selected")
  /** What a date field says when what is in it is not a date. */
  def invalid: String = word("date.invalid", "Not a date")
  /** What a date field says of a date outside the ones allowed. */
  def outOfRange: String = word("date.outOfRange", "Out of range")
  /** What a range field says of an end before its start. */
  def endBeforeStart: String = word("date.endBeforeStart", "Ends before it starts")
  /** A date picker's headline with nothing chosen. */
  def noDate: String = word("date.noDate", "No date")
}
