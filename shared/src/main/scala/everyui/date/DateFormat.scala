package everyui.date

import everyui.i18n.{DateText, Strings}

/** How a date field writes a date and reads one typed into it. Each of the ones here follows the language in use
  * (`Strings.language`) as it is asked, so a field bound to one changes with it; `read` takes anything `DateParse`
  * does, with the numbers in the order the format writes them.
  *
  * {{{
  *   DateFormat.numeric.write(date)                    // "9/29/2026", "29/09/2026", "2026/9/29"
  *   DateFormat.medium.write(date)                     // "Sep 29, 2026", "29 sept 2026"
  *   DateFormat.pattern("dd.MM.yyyy").read("3.4.26")   // Some(2026-04-03)
  *   DateFormat(_.iso, CalendarDate.parse)             // the app's own
  * }}} */
case class DateFormat(write: CalendarDate => String, read: String => Option[CalendarDate])

object DateFormat {
  /** In numbers, as the language writes them (CLDR's `yMd`): what a date field shows unless it says. */
  def numeric: DateFormat = DateFormat(d => DateText.numericDate(d.year, d.month, d.day), t => DateParse.read(t))

  /** The month cut short, as the language writes it (CLDR's `yMMMd`): "Sep 29, 2026". */
  def medium: DateFormat = DateFormat(d => DateText.mediumDate(d.year, d.month, d.day),
    t => DateParse.read(t, DateText.symbols(Strings.language().code).mediumDate))

  /** In full, with the weekday (CLDR's `yMMMMEEEEd`): "Tuesday, September 29, 2026". */
  def full: DateFormat = DateFormat(d => DateText.fullDate(d.year, d.month, d.day, d.weekday),
    t => DateParse.read(t, DateText.symbols(Strings.language().code).fullDate))

  /** ISO 8601, `2026-09-29`, whatever the language; it reads the other forms too. */
  val iso: DateFormat = DateFormat(d => d.iso, t => DateParse.read(t, "yMd"))

  /** The app's own pattern, CLDR's letters (`dd/MM/y`, `d. MMMM y`, `EEE, MMM d, y`; `yyyy` is the year, `yy` its last
    * two digits), in the language's month and weekday names. */
  def pattern(form: String): DateFormat = DateFormat(d => DateText.pattern(form, d.year, d.month, d.day, d.weekday),
    t => DateParse.read(t, form))
}
