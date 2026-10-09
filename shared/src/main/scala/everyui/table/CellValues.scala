package everyui.table

import everyui.render.TableAccess

/** What a cell's words are worth to a sort: a number, tolerating what is written around one (thousands separators,
  * a currency, a sign or brackets for a loss, a percentage, a unit, a k, M or B for thousands, millions or billions);
  * a date (ISO's, with or without a time, the slashed and dotted day-month-year orders, and English month names); or
  * words, in the language's own order. A blank cell, or a dash or "n/a" standing for nothing, is no value at all, and
  * a column's `kind`, where it is not given, is what every cell that says something is. */
object CellValues {
  private val currencies = "$€£¥₹₩₽₺₪฿¢₫₦₱₴₲₡₵₸₭₮"
  private val placeholders = Set("", "-", "—", "–", "n/a", "na", "?", "…", "null", "none")
  private val months = Vector("january", "february", "march", "april", "may", "june", "july", "august", "september",
    "october", "november", "december")
  private val weekdays = Set("mon", "tue", "wed", "thu", "fri", "sat", "sun")
  private val multipliers: Map[String, Double] = Map("k" -> 1e3, "K" -> 1e3, "M" -> 1e6, "B" -> 1e9, "bn" -> 1e9,
    "kB" -> 1e3, "KB" -> 1e3, "MB" -> 1e6, "GB" -> 1e9, "TB" -> 1e12, "PB" -> 1e15,
    "KiB" -> 1024.0, "MiB" -> 1048576.0, "GiB" -> 1073741824.0, "TiB" -> 1099511627776.0)

  /** Whether `text` says nothing: blank, or a dash, "n/a" or the like standing for nothing. */
  def blank(text: String): Boolean = placeholders.contains(text.trim.toLowerCase)

  /** The number `text` writes, if it writes one: "1,234.5", "1.234,5", "$12", "-€3.50", "(42)", "12%", "3.5 GB",
    * "1.2k", "−7". A date, a time, a version or words with a number in them are not numbers. */
  def number(text: String): Option[Double] = {
    var s = text.filter(c => c != ' ' && c != ' ' && c != ' ' && c != ' ')
    var negative = false
    if (s.length > 2 && s.startsWith("(") && s.endsWith(")")) {
      negative = true
      s = s.substring(1, s.length - 1)
    }
    val first = s.indexWhere(c => c.isDigit || c == '.' || c == ',')
    if (first < 0) None
    else {
      val prefix = s.substring(0, first)
      var end = first
      while (end < s.length && (s.charAt(end).isDigit || s.charAt(end) == '.' || s.charAt(end) == ',' ||
        s.charAt(end) == '\'')) end += 1
      val body = s.substring(first, end)
      val suffix = s.substring(end)
      if (prefix.contains("-") || prefix.contains("−")) negative = !negative
      val prefixOk = prefix.length <= 4 && prefix.forall(c => c == '-' || c == '−' || c == '+' || c == '~' ||
        c == '≈' || currencies.indexOf(c.toInt) >= 0 || c.isLetter) && prefix.count(_.isLetter) <= 3
      val suffixOk = suffix.length <= 6 && suffix.forall(c => c.isLetter || c == '%' || c == '‰' || c == '/' ||
        c == '+' || c == '°' || c == '²' || c == '³' || currencies.indexOf(c.toInt) >= 0)
      if (!prefixOk || !suffixOk || !body.exists(_.isDigit)) None
      else separated(body.replace("'", "")).map { v =>
        val times = multipliers.getOrElse(suffix, 1.0)
        val signed = v * times
        if (negative) -signed else signed
      }
    }
  }

  /** Digits with separators read as a number: where both a comma and a point are written, the last is the decimal
    * one; a lone comma before three digits groups thousands, as several commas or several points do (each group then
    * three digits long); any other lone comma or point is the decimal one. */
  private def separated(body: String): Option[Double] = {
    val commas = body.count(_ == ',')
    val points = body.count(_ == '.')
    def grouped(by: Char, decimal: Char): Option[Double] = {
      val (whole, fraction) = if (decimal != ' ' && body.indexOf(decimal.toInt) >= 0)
        (body.substring(0, body.lastIndexOf(decimal.toInt)), body.substring(body.lastIndexOf(decimal.toInt) + 1))
      else (body, "")
      val groups = pieces(whole, by)
      val fine = groups.nonEmpty && groups.head.nonEmpty && groups.head.length <= 3 && groups.tail.forall(_.length == 3) &&
        groups.forall(_.forall(_.isDigit)) && fraction.forall(_.isDigit) && (decimal == ' ' || fraction.nonEmpty)
      if (!fine) None else plain(groups.mkString + (if (fraction.isEmpty) "" else "." + fraction))
    }
    if (commas == 0 && points == 0) plain(body)
    else if (commas > 0 && points > 0) {
      if (body.lastIndexOf(',') > body.lastIndexOf('.')) grouped('.', ',') else grouped(',', '.')
    }
    else if (commas > 1) grouped(',', ' ')
    else if (points > 1) grouped('.', ' ')
    else {
      val mark = if (commas == 1) ',' else '.'
      val at = body.indexOf(mark.toInt)
      val after = body.length - at - 1
      if (mark == ',' && after == 3 && at >= 1 && at <= 3) grouped(',', ' ')
      else plain(body.replace(',', '.'))
    }
  }

  // `text` cut at each `by`, the empty pieces kept (no regular expression: this runs for every cell of a column)
  private def pieces(text: String, by: Char): Vector[String] = {
    var out = Vector.empty[String]
    var from = 0
    var at = text.indexOf(by.toInt)
    while (at >= 0) {
      out = out :+ text.substring(from, at)
      from = at + 1
      at = text.indexOf(by.toInt, from)
    }
    out :+ text.substring(from)
  }

  private def plain(digits: String): Option[Double] =
    if (digits.isEmpty || digits == "." || !digits.forall(c => c.isDigit || c == '.') || digits.count(_ == '.') > 1) None
    else try Some(digits.toDouble) catch { case _: NumberFormatException => None }

  /** When `text` says, as seconds from 1970 (a time zone given is taken into account; none is UTC), if it writes a
    * date: "2026-10-09", "2026-10-09T14:30:00Z", "2026-10-09 14:30", "2026/10/09", "10/9/2026" (the month first,
    * unless the first number cannot be one), "09.10.2026" (the day first), "Oct 9, 2026", "9 October 2026",
    * "Fri, 9 Oct 2026 14:30", "October 2026", "2026-10". */
  def date(text: String): Option[Long] = {
    val s = text.trim
    if (s.length < 6 || s.length > 40) None
    else {
      // its words, ISO's date and time apart where a T joins them
      val parts = pieces(s.replace(',', ' ').replace('\t', ' '), ' ').filter(_.nonEmpty).flatMap { w =>
        val t = w.indexOf('T')
        if (t >= 8 && t < w.length - 1 && w.charAt(t - 1).isDigit && w.charAt(t + 1).isDigit)
          Vector(w.substring(0, t), w.substring(t + 1))
        else Vector(w)
      }
      val dayFirst = parts.headOption.map(_.toLowerCase.take(3)).exists(weekdays.contains)
      val words = if (dayFirst) parts.tail else parts
      if (words.isEmpty) None
      else numeric(words) match {
        case Some((ymd, rest)) => withTime(ymd, rest)
        case None => named(words)
      }
    }
  }

  /** A date of numbers (its first word), and the words after it. */
  private def numeric(words: Vector[String]): Option[((Int, Int, Int), Vector[String])] = {
    val w = words.head
    val sep = if (w.contains("-")) "-" else if (w.contains("/")) "/" else if (w.contains(".")) "." else ""
    if (sep.isEmpty) None
    else {
      val bits = pieces(w, sep.charAt(0))
      if (!bits.forall(b => b.nonEmpty && b.length <= 4 && b.forall(_.isDigit))) None
      else {
        val n = bits.map(_.toInt)
        val ymd: Option[(Int, Int, Int)] =
          if (bits.size == 3 && bits.head.length == 4) Some((n(0), n(1), n(2)))
          else if (bits.size == 2 && bits.head.length == 4 && sep == "-") Some((n(0), n(1), 1))
          else if (bits.size == 3 && (bits(2).length == 4 || bits(2).length == 2) && bits.head.length <= 2) {
            val year = if (bits(2).length == 2) (if (n(2) < 70) 2000 + n(2) else 1900 + n(2)) else n(2)
            // dots and dashes put the day first, as Europe writes them; slashes the month, unless it cannot be one
            if (sep == "/" && n(0) <= 12) Some((year, n(0), n(1))) else Some((year, n(1), n(0)))
          }
          else None
        ymd.filter(valid).map(d => (d, words.tail))
      }
    }
  }

  /** A date with a month's name: "Oct 9 2026", "9 October 2026", "October 2026", and any time after it. */
  private def named(words: Vector[String]): Option[Long] = {
    def month(w: String): Option[Int] = {
      val l = w.toLowerCase.stripSuffix(".")
      if (l.length < 3) None else months.indexWhere(m => m.startsWith(l) || (l == "sept" && m == "september")) match {
        case -1 => None
        case i => Some(i + 1)
      }
    }
    def day(w: String): Option[Int] = {
      val d = w.toLowerCase.stripSuffix("st").stripSuffix("nd").stripSuffix("rd").stripSuffix("th")
      if (d.nonEmpty && d.length <= 2 && d.forall(_.isDigit)) Some(d.toInt) else None
    }
    def year(w: String): Option[Int] = if (w.length == 4 && w.forall(_.isDigit)) Some(w.toInt) else None
    val found: Option[((Int, Int, Int), Vector[String])] =
      if (words.size >= 3 && month(words(0)).nonEmpty && day(words(1)).nonEmpty && year(words(2)).nonEmpty)
        Some(((year(words(2)).get, month(words(0)).get, day(words(1)).get), words.drop(3)))
      else if (words.size >= 3 && day(words(0)).nonEmpty && month(words(1)).nonEmpty && year(words(2)).nonEmpty)
        Some(((year(words(2)).get, month(words(1)).get, day(words(0)).get), words.drop(3)))
      else if (words.size >= 2 && month(words(0)).nonEmpty && year(words(1)).nonEmpty)
        Some(((year(words(1)).get, month(words(0)).get, 1), words.drop(2)))
      else None
    found.filter(f => valid(f._1)).flatMap(f => withTime(f._1, f._2))
  }

  private def valid(d: (Int, Int, Int)): Boolean =
    d._2 >= 1 && d._2 <= 12 && d._3 >= 1 && d._3 <= daysIn(d._1, d._2) && d._1 >= 1 && d._1 <= 9999

  private def daysIn(year: Int, month: Int): Int =
    if (month == 2) (if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28)
    else if (month == 4 || month == 6 || month == 9 || month == 11) 30 else 31

  /** The date at the time `rest` says (nothing, "14:30", "2:30 pm", "14:30:05.120Z", "14:30+02:00", "14:30 UTC"). */
  private def withTime(d: (Int, Int, Int), rest: Vector[String]): Option[Long] = {
    val days = epochDay(d._1, d._2, d._3)
    if (rest.isEmpty) Some(days * 86400L)
    else {
      var t = rest.head
      var zone = 0L
      // a zone after the time: Z, +02:00, -0500
      val z = math.max(t.lastIndexOf('+'), t.lastIndexOf('-'))
      if (t.endsWith("Z") || t.endsWith("z")) t = t.dropRight(1)
      else if (z > 0) {
        val off = t.substring(z + 1).replace(":", "")
        if (off.length == 4 && off.forall(_.isDigit))
          zone = (off.take(2).toLong * 3600L + off.drop(2).toLong * 60L) * (if (t.charAt(z) == '+') 1L else -1L)
        t = t.substring(0, z)
      }
      val hms = pieces(t, ':')
      val after = rest.tail.map(_.toLowerCase)
      val meridian = after.headOption.filter(a => a == "am" || a == "pm" || a == "a.m." || a == "p.m.")
      val leftover = after.drop(meridian.size).filterNot(a => a == "utc" || a == "gmt")
      // hours and minutes of digits, seconds with a fraction or none
      def digits(p: String): Boolean = p.nonEmpty && p.length <= 2 && p.forall(_.isDigit)
      val fine = hms.size >= 2 && hms.size <= 3 && digits(hms(0)) && digits(hms(1)) &&
        (hms.size == 2 || (digits(hms(2).takeWhile(_ != '.')) && hms(2).dropWhile(_ != '.').drop(1).forall(_.isDigit))) &&
        leftover.isEmpty
      if (!fine) None
      else {
        var hour = hms(0).toInt
        val minute = hms(1).toInt
        val second = if (hms.size == 3) hms(2).takeWhile(_ != '.').toInt else 0
        meridian.foreach(m => hour = (hour % 12) + (if (m.startsWith("p")) 12 else 0))
        if (hour > 23 || minute > 59 || second > 60) None
        else Some(days * 86400L + hour * 3600L + minute * 60L + second - zone)
      }
    }
  }

  /** Days from 1970-01-01 to `year`-`month`-`day` (Howard Hinnant's days_from_civil). */
  private def epochDay(year: Int, month: Int, day: Int): Long = {
    val y = (if (month <= 2) year - 1 else year).toLong
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    era * 146097 + doe - 719468
  }

  /** What a column of `values` holds: dates where every one that says something is a date, numbers where every one is
    * a number, else words (and words where none says anything). The first `enough` that say something decide it. */
  def infer(values: Seq[String], enough: Int = 200): ColumnKind = {
    val said = values.iterator.filterNot(blank).take(enough).toVector
    if (said.isEmpty) ColumnKind.Text
    else if (said.forall(v => date(v).nonEmpty)) ColumnKind.Date
    else if (said.forall(v => number(v).nonEmpty)) ColumnKind.Number
    else ColumnKind.Text
  }

  /** The indices of `values` in the order of `kind`, up or down: those with no value (blank, or not of the kind) after
    * the rest either way, and equal values in the order they came. Words are compared as the language orders them
    * (`TableAccess.collate`: the browser's or the platform's collation, numbers in words by their value on the web). */
  def order(values: Vector[String], kind: ColumnKind, ascending: Boolean): Vector[Int] = {
    val sign = if (ascending) 1 else -1
    val indices = values.indices.toVector
    kind match {
      case ColumnKind.Number =>
        val keys = values.map(v => if (blank(v)) None else number(v))
        indices.sortWith((a, b) => before(keys(a), keys(b), a, b, sign)((x, y) => java.lang.Double.compare(x, y)))
      case ColumnKind.Date =>
        val keys = values.map(v => if (blank(v)) None else date(v))
        indices.sortWith((a, b) => before(keys(a), keys(b), a, b, sign)((x, y) => java.lang.Long.compare(x, y)))
      case ColumnKind.Text =>
        val keys = values.map(v => if (blank(v)) None else Some(v.trim))
        indices.sortWith((a, b) => before(keys(a), keys(b), a, b, sign)((x, y) => TableAccess.collate(x, y)))
    }
  }

  private def before[K](x: Option[K], y: Option[K], a: Int, b: Int, sign: Int)(compare: (K, K) => Int): Boolean =
    (x, y) match {
      case (Some(p), Some(q)) =>
        val c = compare(p, q) * sign
        if (c != 0) c < 0 else a < b
      case (Some(_), None) => true
      case (None, Some(_)) => false
      case _ => a < b
    }
}
