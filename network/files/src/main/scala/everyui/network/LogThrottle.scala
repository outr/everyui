package everyui.network

/** How much an app may say to its server's log: no more than `perMinute` lines a minute in all, and a line not again
  * inside `repeatMs` once its ids and numbers are taken out (`LogThrottle.normal`), so "card 812 failed" and "card 813
  * failed" are the one line. A failure that repeats every second (a room polled while the server is down) is one line
  * a minute rather than sixty, and a device in trouble, which is the one reporting, is never handed a request a frame.
  * A line held back is dropped, not queued. */
class LogThrottle(perMinute: Int = 20, repeatMs: Double = 60000.0) {
  private var minuteStart = -1.0
  private var sent = 0
  private var recent: Map[String, Double] = Map.empty

  /** Whether `line` may go at `now` (milliseconds), counting it if so. */
  def allow(line: String, now: Double): Boolean = {
    if (minuteStart < 0.0 || now - minuteStart >= 60000.0) {
      minuteStart = now
      sent = 0
      recent = recent.filter(e => now - e._2 < repeatMs)
    }
    val key = LogThrottle.normal(line)
    if (recent.get(key).exists(at => now - at < repeatMs) || sent >= perMinute) false
    else {
      sent += 1
      recent = recent + (key -> now)
      true
    }
  }
}

object LogThrottle {
  /** `line` with each word that has a digit in it (a number, an id, a hash) put as "#", and an id in pieces (a UUID, a
    * version, a time) as one. */
  def normal(line: String): String = {
    val out = new StringBuilder
    var i = 0
    while (i < line.length) {
      if (!line.charAt(i).isLetterOrDigit) {
        out.append(line.charAt(i))
        i += 1
      }
      else {
        var end = i
        var digit = false
        while (end < line.length && line.charAt(end).isLetterOrDigit) {
          if (line.charAt(end).isDigit) digit = true
          end += 1
        }
        // the pieces of one id (a UUID's, a version's, a time's) joined by a hyphen, a dot or a colon are one "#"
        val joined = out.length >= 2 && out.charAt(out.length - 2) == '#' &&
          "-_.:".indexOf(out.charAt(out.length - 1).toInt) >= 0
        if (digit && joined) out.setLength(out.length - 1)
        else if (digit) out.append('#')
        else out.append(line.substring(i, end))
        i = end
      }
    }
    out.toString
  }
}
