package everyui.log

/** A log line as a server sends it: its place in the server's numbering (`seq`, from 1, 0 for none), when it was
  * written (`at`, milliseconds since the epoch, 0 for unknown), its `level` by name ("ERROR", "WARN", "INFO", "DEBUG",
  * "TRACE", "" for none: `LogLevel.name`), its `text` (which may hold ANSI escapes, and more than one line), and where
  * it came from (`source`, "" for nowhere in particular). Plain fields, so every backend reads and writes it the same
  * (`LogWire`'s codec); a `LogView` takes it as it is (`append(event)`), keeping lines in `seq` order and a seq it has
  * already shown once. */
case class LogEvent(seq: Long, at: Long, level: String, text: String, source: String = "") {
  /** The level it names, if it names one. */
  def logLevel: Option[LogLevel] = if (level.isEmpty) None else LogLevel.parse(level)
}

object LogEvent {
  /** `text` written now, at `level`, from `source`: the server numbers it as it sends it. */
  def of(text: String, level: Option[LogLevel] = None, source: String = ""): LogEvent =
    LogEvent(0L, System.currentTimeMillis(), level.map(LogLevel.name).getOrElse(""), text, source)
}
