package everyui.date

import everyui.i18n.{DateText, Strings}
import everyui.render.Platform

/** A time of day, `hour` 0 to 23 and `minute` 0 to 59, with no date and no zone: when somebody got somewhere. */
case class ClockTime(hour: Int, minute: Int) extends Ordered[ClockTime] {
  /** Minutes since midnight. */
  def minutes: Int = hour * 60 + minute

  /** `m` minutes later (earlier, where negative), round the clock. */
  def plus(m: Int): ClockTime = ClockTime.ofMinutes(minutes + m)

  /** As the language writes it, on its own clock: "3:07 PM", "15:07" (`DateText.timeOfDay`). */
  def text(code: String = Strings.language().code): String = DateText.timeOfDay(hour, minute, code)

  def compare(that: ClockTime): Int = minutes - that.minutes
}

object ClockTime {
  /** The time `m` minutes after midnight, round the clock. */
  def ofMinutes(m: Int): ClockTime = {
    val d = ((m % 1440) + 1440) % 1440
    ClockTime(d / 60, d % 60)
  }

  /** The time on the device's clock now. */
  def now(): ClockTime = {
    val local = Platform.localDateTime(System.currentTimeMillis())
    if (local.length >= 16) ClockTime(local.substring(11, 13).toIntOption.getOrElse(0),
      local.substring(14, 16).toIntOption.getOrElse(0))
    else ClockTime(0, 0)
  }

  /** `t` taken to the nearest `step` minutes (on to the next for a half). */
  def rounded(t: ClockTime, step: Int): ClockTime =
    if (step <= 1) t else ofMinutes(math.round(t.minutes.toDouble / step).toInt * step)

  /** What `text` says, as people type a time: "14:30", "2:30 pm", "2:30PM", "2.30", "1430", "230", "2 pm", the
    * language's own morning and afternoon words ("下午3:07"); None for anything that is not a time. A morning or an
    * afternoon word takes a 12-hour time to it; without one, a time is read on the 24-hour clock. */
  def read(text: String, code: String = Strings.language().code): Option[ClockTime] = {
    val s = text.trim.toLowerCase
    if (s.isEmpty) None
    else {
      val symbols = DateText.symbols(code)
      def says(word: String): Boolean = word.nonEmpty && s.contains(word.toLowerCase)
      val pm = says(symbols.pm) || s.contains("pm") || s.endsWith("p") || s.contains("p.m")
      val am = !pm && (says(symbols.am) || s.contains("am") || s.endsWith("a") || s.contains("a.m"))
      // the digits, and where the hour ends: at a separator, else by how many there are
      val groups = s.split("[^0-9]+").filter(_.nonEmpty).toList
      val (h, m) = groups match {
        case List(one) if one.length <= 2 => (one.toIntOption, Some(0))
        case List(one) if one.length == 3 => (one.take(1).toIntOption, one.drop(1).toIntOption)
        case List(one) if one.length == 4 => (one.take(2).toIntOption, one.drop(2).toIntOption)
        case hh :: mm :: _ if hh.length <= 2 && mm.length == 2 => (hh.toIntOption, mm.toIntOption)
        case _ => (None, None)
      }
      (h, m) match {
        case (Some(hour), Some(minute)) if minute >= 0 && minute < 60 =>
          if (am || pm) {
            if (hour < 1 || hour > 12) None else Some(ClockTime(hour % 12 + (if (pm) 12 else 0), minute))
          }
          else if (hour >= 0 && hour < 24) Some(ClockTime(hour, minute))
          else None
        case _ => None
      }
    }
  }
}
