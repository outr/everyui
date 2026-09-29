package everyui.date

import everyui.i18n.{DateText, Strings}

/** A date read from what somebody typed, the way they would write it in their language: `2026-09-29`, `9/29/2026`,
  * `29.9.26`, `Sep 29 2026`, `29 septembre 2026`, `2026年9月29日`, `20260929`.
  *
  * Numbers alone are taken in the order `order` puts the day, the month and the year in (a pattern: its first `d`, `M`
  * and `y`; the language's numeric date by default, so `9/10` is September 10 in English and October 9 in French),
  * except that a number of three or more digits first is the year. A month may be named, in full or cut short, in the
  * language or in English, and weekday names are passed over. A year left out is `reference`'s; a year of two digits is
  * the one nearest `reference` within 80 years before it and 20 after. Arabic-Indic digits read as their numbers.
  * Anything that is not a day on the calendar (February 30) is None. */
object DateParse {
  def read(text: String, order: String = "", reference: CalendarDate = CalendarDate.today(),
           code: String = Strings.language().code): Option[CalendarDate] = {
    val plain = digits(text.trim)
    if (plain.isEmpty) None
    else CalendarDate.parse(plain).orElse {
      val fields = fieldOrder(if (order.isEmpty) DateText.symbols(code).numericDate else order)
      val tokens = split(plain)
      val numbers = tokens.filter(t => number(t))
      val words = tokens.filterNot(t => number(t))
      val named = words.map(w => monthNamed(w, code))
      val unknown = words.zip(named).exists { case (w, m) => m.isEmpty && !passed(w, code) }
      val months = named.flatten
      if (unknown || months.length > 1) None
      else if (months.length == 1) withMonth(months.head, numbers, fields, reference)
      else if (numbers.length == 1 && numbers.head.length == 8) compact(numbers.head, fields)
      else if (numbers.length == 3) inOrder(numbers, fields, reference)
      else if (numbers.length == 2) {
        // a day and a month, in the order they come in the pattern, this year
        val dayFirst = fields.indexOf('d') < fields.indexOf('M')
        val (d, m) = if (dayFirst) (numbers(0), numbers(1)) else (numbers(1), numbers(0))
        CalendarDate.of(reference.year, m.toInt, d.toInt)
      }
      else None
    }
  }

  /** The order of the day, the month and the year in a CLDR pattern: as a word of `d`, `M` and `y` alone
    * (`M/d/y` is "Mdy", `d. MMMM y` "dMy", `y年M月d日` "yMd"). */
  def fieldOrder(pattern: String): String = {
    var found = ""
    var quoted = false
    var i = 0
    while (i < pattern.length) {
      val c = pattern.charAt(i)
      if (c == '\'') quoted = !quoted
      else if (!quoted && (c == 'd' || c == 'M' || c == 'L' || c == 'y')) {
        val f = if (c == 'L') 'M' else c
        if (found.indexOf(f) < 0) found = found + f.toString
      }
      i += 1
    }
    // a pattern missing a field has it where English puts it
    if (found.indexOf('M') < 0) found = found + "M"
    if (found.indexOf('d') < 0) found = found + "d"
    if (found.indexOf('y') < 0) found = found + "y"
    found
  }

  /** The year a two-digit `yy` means, seen from `reference`: the nearest within 80 years before it and 20 after. */
  def century(yy: Int, reference: Int): Int = {
    val base = reference - 80
    val start = base - ((base % 100) + 100) % 100
    val first = start + yy
    if (first < base) first + 100 else first
  }

  private def withMonth(month: Int, numbers: List[String], fields: String, reference: CalendarDate): Option[CalendarDate] =
    if (numbers.length == 1) CalendarDate.of(reference.year, month, numbers.head.toInt)
    else if (numbers.length == 2) {
      val a = numbers(0)
      val b = numbers(1)
      // the year is the one with three or more digits, or past 31; else where the pattern puts it
      val aYear = a.length >= 3 || a.toInt > 31
      val bYear = b.length >= 3 || b.toInt > 31
      val yearFirst = if (aYear != bYear) aYear else fields.indexOf('y') < fields.indexOf('d')
      val (y, d) = if (yearFirst) (a, b) else (b, a)
      CalendarDate.of(year(y, reference), month, d.toInt)
    }
    else None

  private def inOrder(numbers: List[String], fields: String, reference: CalendarDate): Option[CalendarDate] = {
    // a first number of three or more digits is a year, and year-month-day follows it
    val order = if (numbers.head.length >= 3) "yMd" else fields
    val y = numbers(order.indexOf('y'))
    val m = numbers(order.indexOf('M'))
    val d = numbers(order.indexOf('d'))
    if (m.length > 2 || d.length > 2) None else CalendarDate.of(year(y, reference), m.toInt, d.toInt)
  }

  // eight digits together: the year's four and two each for the month and the day, in the pattern's order, else with
  // the year first
  private def compact(n: String, fields: String): Option[CalendarDate] =
    compactIn(n, if (fields.startsWith("y")) "yMd" else fields).orElse(compactIn(n, "yMd"))

  private def compactIn(n: String, order: String): Option[CalendarDate] = {
    // each field's start: the year takes four digits, the others two
    var ys = 0
    var ms = 0
    var ds = 0
    var at = 0
    var i = 0
    while (i < 3) {
      val f = order.charAt(i)
      if (f == 'y') ys = at else if (f == 'M') ms = at else ds = at
      at = at + (if (f == 'y') 4 else 2)
      i += 1
    }
    CalendarDate.of(n.substring(ys, ys + 4).toInt, n.substring(ms, ms + 2).toInt, n.substring(ds, ds + 2).toInt)
  }

  private def year(y: String, reference: CalendarDate): Int =
    if (y.length <= 2) century(y.toInt, reference.year) else y.toInt

  private def number(token: String): Boolean = token.nonEmpty && token.forall(c => c >= '0' && c <= '9')

  /** The month `word` names, in the language or in English: in full, cut short as the language cuts it, or its first
    * three letters or more. */
  private def monthNamed(word: String, code: String): Option[Int] = {
    val w = word.toLowerCase
    var found: Option[Int] = None
    var m = 1
    while (found.isEmpty && m <= 12) {
      if (names(m, code).exists(n => n == w || (w.length >= 3 && n.startsWith(w)))) found = Some(m)
      m += 1
    }
    found
  }

  private def names(month: Int, code: String): List[String] =
    List(DateText.monthName(month, false, code), DateText.monthName(month, true, code),
      DateText.monthName(month, false, "en"), DateText.monthName(month, true, "en"))
      .map(n => clean(n.toLowerCase)).filter(_.nonEmpty)

  /** A word to pass over: a weekday's name, or a short joining word ("de", "of", "the"). */
  private def passed(word: String, code: String): Boolean = {
    val w = word.toLowerCase
    w.length <= 2 || w == "the" || w == "del" || weekday(w, code)
  }

  private def weekday(w: String, code: String): Boolean = {
    var found = false
    var d = 1
    while (!found && d <= 7) {
      // a name of two words ("sexta-feira") passes over each of them
      val all = List(DateText.weekdayName(d, false, code), DateText.weekdayName(d, true, code),
        DateText.weekdayName(d, false, "en"), DateText.weekdayName(d, true, "en")).flatMap(n => split(clean(n.toLowerCase)))
      if (all.exists(n => n == w || (w.length >= 3 && n.startsWith(w)))) found = true
      d += 1
    }
    found
  }

  // a name without the full stop that cuts it short ("sept." as "sept")
  private def clean(name: String): String = if (name.endsWith(".")) name.substring(0, name.length - 1) else name

  /** The text in pieces: runs of digits, and runs of anything else that is not a separator (space, punctuation, and
    * the characters a CJK date writes between its numbers). */
  private def split(text: String): List[String] = {
    var tokens: List[String] = Nil
    var current = ""
    var digitRun = false
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (separator(c)) {
        if (current.nonEmpty) tokens = tokens :+ current
        current = ""
      }
      else {
        val isDigit = c >= '0' && c <= '9'
        if (current.nonEmpty && isDigit != digitRun) {
          tokens = tokens :+ current
          current = ""
        }
        digitRun = isDigit
        current = current + c.toString
      }
      i += 1
    }
    if (current.nonEmpty) tokens = tokens :+ current
    tokens
  }

  private def separator(c: Char): Boolean =
    c == ' ' || c == '/' || c == '-' || c == '.' || c == ',' || c == '\'' || c == '،' || c == '‏' ||
      c == '‎' || c == ' ' || c == '年' || c == '月' || c == '日' || c == '\t'

  // Arabic-Indic and Eastern Arabic-Indic digits as ASCII ones
  private def digits(text: String): String = {
    var out = ""
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      out = out + (if (c >= '٠' && c <= '٩') (c - '٠').toString
        else if (c >= '۰' && c <= '۹') (c - '۰').toString
        else c.toString)
      i += 1
    }
    out
  }
}
