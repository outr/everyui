package everyui.i18n

/** Dates and times written the way a language writes them: its month and weekday names, the order it puts them in,
  * and whether its clock runs to 12 or 24 hours. Each form is one of CLDR's skeletons (as `intl`'s `DateFormat`
  * names them), read from `DateSymbols` for the language in use; a language without symbols is written as English.
  *
  * Every argument is a calendar value, not a moment: the caller reads the local date and time where it is (the
  * backends' `Platform.localDateTime`), so this knows nothing of zones. Months are 1-12, weekdays 1 (Monday) to 7. */
object DateText {
  /** "Jan 15, 2013", "15 ene 2013", "2013年1月15日": CLDR's `yMMMd`. */
  def mediumDate(year: Int, month: Int, day: Int, code: String = Strings.language().code): String =
    write(symbols(code).mediumDate, year, month, day, 0, 0, 0, symbols(code))

  /** "Jan 15", "15 ene": CLDR's `MMMd`. */
  def monthDay(month: Int, day: Int, code: String = Strings.language().code): String =
    write(symbols(code).monthDay, 0, month, day, 0, 0, 0, symbols(code))

  /** "Wed, Sep 16", "mié, 16 sept": CLDR's `MMMEd`. */
  def weekdayMonthDay(month: Int, day: Int, weekday: Int, code: String = Strings.language().code): String =
    write(symbols(code).weekdayMonthDay, 0, month, day, weekday, 0, 0, symbols(code))

  /** "Wednesday, September 16", "miércoles, 16 de septiembre": CLDR's `MMMMEEEEd`. */
  def longWeekdayDate(month: Int, day: Int, weekday: Int, code: String = Strings.language().code): String =
    write(symbols(code).longWeekdayDate, 0, month, day, weekday, 0, 0, symbols(code))

  /** "3:07 PM", "15:07", "下午3:07": CLDR's `jm`, the language's own clock. */
  def timeOfDay(hour: Int, minute: Int, code: String = Strings.language().code): String =
    write(symbols(code).timeOfDay, 0, 0, 0, 0, hour, minute, symbols(code))

  /** Whether the language's clock runs to 12 hours (English, Arabic, Chinese) rather than 24. */
  def twelveHour(code: String = Strings.language().code): Boolean = symbols(code).timeOfDay.contains("h")

  /** A month's name ("September", "septiembre"), or its short form ("Sep", "sept"). */
  def monthName(month: Int, short: Boolean = false, code: String = Strings.language().code): String = {
    val s = symbols(code)
    val names = if (short) s.shortMonths else s.months
    if (month < 1 || month > 12) "" else names(month - 1)
  }

  /** A weekday's name ("Wednesday", "miércoles"), or its short form, 1 being Monday. */
  def weekdayName(weekday: Int, short: Boolean = false, code: String = Strings.language().code): String = {
    val s = symbols(code)
    val names = if (short) s.shortWeekdays else s.weekdays
    if (weekday < 1 || weekday > 7) "" else names(weekday - 1)
  }

  /** The symbols for `code`: its own, those of its language without the script or region (`pt_BR` as `pt`), else
    * English's. Traditional Chinese is filed as `zh_Hant`. */
  def symbols(code: String): DateSymbols = {
    val normal = code.replace('-', '_')
    DateSymbols.all.find(_.code == normal)
      .orElse(if (normal == "zh_TW" || normal == "zh_HK" || normal == "zh_MO") DateSymbols.all.find(_.code == "zh_Hant") else None)
      .orElse(DateSymbols.all.find(_.code == normal.takeWhile(_ != '_')))
      .getOrElse(DateSymbols.English)
  }

  /** `pattern` with its fields filled in, CLDR's way: a run of one letter is one field (`y`, `M`, `MMM`, `MMMM`, `d`,
    * `dd`, `EEE`, `EEEE`, `h`, `H`, `HH`, `mm`, `a`), text between single quotes is written as it is, and anything
    * else is kept. */
  private def write(pattern: String, year: Int, month: Int, day: Int, weekday: Int, hour: Int, minute: Int,
                    s: DateSymbols): String = {
    var out = ""
    var i = 0
    while (i < pattern.length) {
      val c = pattern.charAt(i)
      if (c == '\'') {
        val end = pattern.indexOf('\'', i + 1)
        val stop = if (end < 0) pattern.length else end
        out = out + pattern.substring(i + 1, stop)
        i = stop + 1
      } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        var j = i
        while (j < pattern.length && pattern.charAt(j) == c) j += 1
        out = out + field(c, j - i, year, month, day, weekday, hour, minute, s)
        i = j
      } else {
        out = out + c.toString
        i += 1
      }
    }
    out
  }

  private def field(c: Char, count: Int, year: Int, month: Int, day: Int, weekday: Int, hour: Int, minute: Int,
                    s: DateSymbols): String = {
    val two: Int => String = n => (if (n < 10) "0" else "") + n.toString
    if (c == 'y') year.toString
    else if (c == 'M' || c == 'L') {
      if (count >= 4) monthOf(s.months, month)
      else if (count == 3) monthOf(s.shortMonths, month)
      else if (count == 2) two(month)
      else month.toString
    }
    else if (c == 'd') { if (count >= 2) two(day) else day.toString }
    else if (c == 'E' || c == 'c') {
      if (weekday < 1 || weekday > 7) ""
      else if (count >= 4) s.weekdays(weekday - 1)
      else s.shortWeekdays(weekday - 1)
    }
    else if (c == 'h') {
      val h12 = if (hour % 12 == 0) 12 else hour % 12
      if (count >= 2) two(h12) else h12.toString
    }
    else if (c == 'H') { if (count >= 2) two(hour) else hour.toString }
    else if (c == 'm') two(minute)
    else if (c == 'a') { if (hour < 12) s.am else s.pm }
    else ""
  }

  private def monthOf(names: List[String], month: Int): String = if (month < 1 || month > 12) "" else names(month - 1)
}
