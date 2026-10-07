package everyui.log

/** Which lines a `LogView` shows: those whose words hold `text` (a regular expression with `regex`; any case unless
  * `caseSensitive`), and of `levels` (all of them while it is empty; with any named, a line of no level is hidden).
  * Its markers show whatever it says.
  *
  * {{{
  *   log.filter @= LogFilter(text = "timeout", levels = Set(LogLevel.Warn, LogLevel.Error))
  * }}} */
case class LogFilter(text: String = "", regex: Boolean = false, caseSensitive: Boolean = false,
                     levels: Set[LogLevel] = Set.empty) {
  /** Whether it lets every line through. */
  def open: Boolean = text.isEmpty && levels.isEmpty
}
