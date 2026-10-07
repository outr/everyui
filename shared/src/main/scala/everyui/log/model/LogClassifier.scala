package everyui.log

/** A line's level, read from its words: what a `LogView` classifies lines by unless the app says otherwise
  * (`LogView.level`).
  *
  * It looks near the start of the line for the first word that names a level. In capitals it is one wherever it
  * stands (scribe's `2026.08.28 17:46:50 [main] INFO dw.Runner:87 - …`, logback's `12:00:01.123 [main] WARN c.Foo -
  * …`, java.util.logging's `SEVERE:`); in any case it is one where it is marked as one: bracketed (`[error]`, sbt's
  * `[warn]`), followed by a colon (`error: …`), or a value (`level=info`, `"level":"warn"`). Android's logcat names
  * its levels by a letter: `E/Tag(123): …` and `09-27 12:00:00.123  1234  1234 W Tag: …`. A line naming none has
  * none. */
object LogClassifier {
  /** How far into a line a level is looked for, in characters. */
  val reach: Int = 200

  /** The default classifier. */
  val default: String => Option[LogLevel] = (line: String) => classify(line)

  // each word, lower case, its level, and whether only its capitals count (java.util.logging's, and words a sentence
  // uses)
  private val words: List[(String, LogLevel, Boolean)] = List(
    ("error", LogLevel.Error, false), ("err", LogLevel.Error, false), ("fatal", LogLevel.Error, false),
    ("severe", LogLevel.Error, true), ("critical", LogLevel.Error, false), ("crit", LogLevel.Error, false),
    ("panic", LogLevel.Error, true), ("warn", LogLevel.Warn, false), ("warning", LogLevel.Warn, false),
    ("info", LogLevel.Info, false), ("notice", LogLevel.Info, false), ("debug", LogLevel.Debug, false),
    ("fine", LogLevel.Debug, true), ("trace", LogLevel.Trace, false), ("finer", LogLevel.Trace, true),
    ("finest", LogLevel.Trace, true))

  /** The level `word` (lower case) names, as `LogLevel.parse` reads it. */
  def named(word: String): Option[LogLevel] = words.find(_._1 == word).map(_._2)

  /** The level `line` names, if it names one. */
  def classify(line: String): Option[LogLevel] = logcat(line).orElse {
    val start = if (line.length > reach) line.substring(0, reach) else line
    val lower = start.toLowerCase
    var best = -1
    var found: Option[LogLevel] = None
    words.foreach { case (word, level, capitals) =>
      var at = lower.indexOf(word)
      while (at >= 0 && (best < 0 || at < best)) {
        val end = at + word.length
        if (bounded(start, at, end) &&
          (start.substring(at, end) == word.toUpperCase || (!capitals && marked(start, at, end)))) {
          best = at
          found = Some(level)
        }
        at = lower.indexOf(word, at + 1)
      }
    }
    found
  }

  // the letter logcat names its levels by: at the start (`E/Tag`), or after the date, time and ids (threadtime)
  private def logcat(line: String): Option[LogLevel] =
    if (line.length > 2 && line.charAt(1) == '/' && tagged(line)) letter(line.charAt(0))
    else if (line.length > 20 && line.charAt(2) == '-' && line.charAt(5) == ' ' && line.charAt(8) == ':' &&
      digit(line.charAt(0))) {
      // the fifth word: date, time, process, thread, level
      var i = 0
      var word = 0
      var found: Option[LogLevel] = None
      while (found.isEmpty && word < 5 && i < line.length) {
        while (i < line.length && line.charAt(i) == ' ') i += 1
        val from = i
        while (i < line.length && line.charAt(i) != ' ') i += 1
        if (word == 4 && i == from + 1) found = letter(line.charAt(from))
        word += 1
      }
      found
    }
    else None

  // a logcat tag after the slash, ended by its colon or its process before any space ("I/O error" is no tag)
  private def tagged(line: String): Boolean = {
    val space = line.indexOf(" ")
    val colon = line.indexOf(":")
    val paren = line.indexOf("(")
    val end = if (colon < 0) paren else if (paren < 0) colon else math.min(colon, paren)
    end > 2 && (space < 0 || end < space)
  }

  private def letter(c: Char): Option[LogLevel] =
    if (c == 'E' || c == 'F' || c == 'A') Some(LogLevel.Error)
    else if (c == 'W') Some(LogLevel.Warn)
    else if (c == 'I') Some(LogLevel.Info)
    else if (c == 'D') Some(LogLevel.Debug)
    else if (c == 'V') Some(LogLevel.Trace)
    else None

  private def digit(c: Char): Boolean = c >= '0' && c <= '9'

  private def wordy(c: Char): Boolean = c.isLetterOrDigit || c == '_'

  // a word on its own: nothing of a word either side
  private def bounded(s: String, from: Int, to: Int): Boolean =
    (from == 0 || !wordy(s.charAt(from - 1))) && (to >= s.length || !wordy(s.charAt(to)))

  // marked as a level: bracketed, followed by a colon, or a value
  private def marked(s: String, from: Int, to: Int): Boolean = {
    val before = if (from > 0) s.charAt(from - 1) else ' '
    val after = if (to < s.length) s.charAt(to) else ' '
    before == '[' || before == '=' || before == '"' || before == '<' ||
      after == ']' || after == ':' || after == '"' || after == '>'
  }
}
