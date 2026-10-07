package everyui.log

import scala.util.matching.Regex

/** Where `query` is in a line's words: as it is, in any case unless `caseSensitive`, or as a regular expression. A
  * regular expression that does not compile matches nothing (`broken`), and nor does one that matches nothing at all
  * (`a*`, `^`). */
class LogMatcher(val query: String, val regex: Boolean = false, val caseSensitive: Boolean = false) {
  private val lower = query.toLowerCase
  private var compiled = true
  private val pattern: Option[Regex] = if (!regex || query.isEmpty) None else {
    try {
      val r = ((if (caseSensitive) "" else "(?i)") + query).r
      if (r.findFirstMatchIn("").isEmpty) Some(r) else None
    } catch {
      case _: Throwable =>
        compiled = false
        None
    }
  }
  /** Whether it is a regular expression that does not compile. */
  val broken: Boolean = !compiled

  /** Whether there is nothing to look for. */
  val empty: Boolean = query.isEmpty || (regex && pattern.isEmpty)

  /** Whether `text` holds it. */
  def matches(text: String): Boolean = !empty && next(text, hay(text), 0)._1 >= 0

  /** How many times `text` holds it, apart. */
  def count(text: String): Int = if (empty) 0 else {
    val in = hay(text)
    var n = 0
    var at = next(text, in, 0)
    while (at._1 >= 0) {
      n += 1
      at = next(text, in, at._2)
    }
    n
  }

  /** Where `text` holds it, each from (included) and to (excluded), in order. */
  def spans(text: String): List[(Int, Int)] = if (empty) Nil else {
    val in = hay(text)
    var out: List[(Int, Int)] = Nil
    var at = next(text, in, 0)
    while (at._1 >= 0) {
      out = at :: out
      at = next(text, in, at._2)
    }
    out.reverse
  }

  // what is looked in: the words lower case where case does not matter and the query is words
  private def hay(text: String): String = if (caseSensitive || pattern.nonEmpty) text else text.toLowerCase

  // the first match at or after `from`, as (from, to), (-1, -1) for none
  private def next(text: String, in: String, from: Int): (Int, Int) = if (from >= text.length) (-1, -1) else pattern match {
    case Some(r) =>
      val rest = text.substring(from)
      r.findFirstMatchIn(rest) match {
        // (found again by its words: a Roku's match says what matched, not where)
        case Some(m) =>
          val words = m.group(0)
          val at = rest.indexOf(words)
          if (at < 0 || words.isEmpty) (-1, -1) else (from + at, from + at + words.length)
        case None => (-1, -1)
      }
    case None =>
      val at = in.indexOf(if (caseSensitive) query else lower, from)
      if (at < 0) (-1, -1) else (at, at + query.length)
  }
}
