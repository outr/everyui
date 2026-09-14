package everyui.util

import everyui.render.Platform

/** Times, spans and counts as a dashboard shows them. */
object Format {
  /** Local `yyyy-MM-dd HH:mm:ss`. */
  def time(millis: Long): String = Platform.localDateTime(millis)

  /** `finished` or, while unfinished, now (`Clock.now`, so a binding ticks): "42s", "3m 12s", "2h 05m". */
  def elapsed(started: Long, finished: Option[Long]): String = {
    val seconds = (finished.getOrElse(Clock.now()) - started) / 1000L
    if (seconds < 60L) seconds.toString + "s"
    else if (seconds < 3600L) (seconds / 60L).toString + "m " + (seconds % 60L).toString + "s"
    else (seconds / 3600L).toString + "h " + ((seconds % 3600L) / 60L).toString + "m"
  }

  /** Whole numbers with thousands separated; decimals only where the value has them. */
  def count(value: Double): String = {
    val rounded = math.round(value)
    if (math.abs(value - rounded.toDouble) < 0.0001) group(rounded) else value.toString
  }

  private def group(value: Long): String = {
    val digits = math.abs(value).toString
    var out = ""
    var head = digits.length
    while (head > 3) {
      out = "," + digits.substring(head - 3, head) + out
      head -= 3
    }
    out = digits.substring(0, head) + out
    if (value < 0) "-" + out else out
  }
}
