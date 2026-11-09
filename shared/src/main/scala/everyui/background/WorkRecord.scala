package everyui.background

/** Work scheduled to run while the app is open (WorkRunner), as it is kept in the app's settings: what was asked
  * (its key, every how many minutes or 0 for once, its constraints, its backoff and its data), when it is next due
  * and last ran (milliseconds since 1970, 0 for never), and how many tries in a row have answered Retry. */
case class WorkRecord(key: String,
                      every: Int,
                      constraints: WorkConstraints,
                      backoff: WorkBackoff,
                      data: Map[String, String],
                      due: Long,
                      lastRun: Long,
                      retries: Int) {
  /** As kept: its fields with `|` between, escaped, its data as `key=value` with `;` between. */
  def encode: String = {
    import WorkRecord.escape
    val c = constraints
    Vector(escape(key), every.toString, c.network.ordinal.toString, flag(c.charging), flag(c.batteryNotLow),
      flag(c.idle), flag(backoff.exponential), backoff.seconds.toString,
      data.toVector.sortBy(_._1).map((k, v) => escape(k) + "=" + escape(v)).mkString(";"),
      due.toString, lastRun.toString, retries.toString).mkString("|")
  }

  private def flag(b: Boolean): String = if (b) "1" else "0"
}

object WorkRecord {
  /** `s` with the characters that separate the fields escaped. */
  def escape(s: String): String = {
    val out = new StringBuilder
    s.foreach {
      case '\\' => out.append("\\\\")
      case '|' => out.append("\\p")
      case ';' => out.append("\\s")
      case '=' => out.append("\\e")
      case c => out.append(c)
    }
    out.toString
  }

  def unescape(s: String): String = {
    val out = new StringBuilder
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '\\' && i + 1 < s.length) {
        s.charAt(i + 1) match {
          case 'p' => out.append('|')
          case 's' => out.append(';')
          case 'e' => out.append('=')
          case other => out.append(other)
        }
        i += 2
      }
      else {
        out.append(c)
        i += 1
      }
    }
    out.toString
  }

  // the parts between unescaped `sep`s, still escaped
  private def split(s: String, sep: Char): Vector[String] = {
    var parts = Vector.empty[String]
    val part = new StringBuilder
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '\\' && i + 1 < s.length) {
        part.append(c).append(s.charAt(i + 1))
        i += 2
      }
      else {
        if (c == sep) {
          parts :+= part.toString
          part.clear()
        }
        else part.append(c)
        i += 1
      }
    }
    parts :+ part.toString
  }

  /** A record as `encode` wrote it, or None where it is not one. */
  def decode(text: String): Option[WorkRecord] = {
    val f = split(text, '|')
    if (f.size != 12) None
    else {
      val number = (s: String) => s.toLongOption
      val networks = WorkNetwork.values
      (f(1).toIntOption, f(2).toIntOption, f(7).toDoubleOption, number(f(9)), number(f(10)), f(11).toIntOption) match {
        case (Some(every), Some(n), Some(seconds), Some(due), Some(last), Some(retries))
          if n >= 0 && n < networks.length =>
          val data = if (f(8).isEmpty) Map.empty[String, String]
            else split(f(8), ';').flatMap { kv =>
              val pair = split(kv, '=')
              if (pair.size == 2) Some(unescape(pair(0)) -> unescape(pair(1))) else None
            }.toMap
          Some(WorkRecord(unescape(f(0)), every, WorkConstraints(networks(n), f(3) == "1", f(4) == "1", f(5) == "1"),
            WorkBackoff(f(6) == "1", seconds), data, due, last, retries))
        case _ => None
      }
    }
  }
}
