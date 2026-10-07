package everyui.log

/** How much a log line matters, least first: what a level filter keeps and a line's level chip names. A fatal,
  * severe or critical line is an Error. */
enum LogLevel {
  case Trace, Debug, Info, Warn, Error
}

object LogLevel {
  /** Every level, least first. */
  val all: List[LogLevel] = List(LogLevel.Trace, LogLevel.Debug, LogLevel.Info, LogLevel.Warn, LogLevel.Error)

  /** How a level is written on the wire and in a chip: "TRACE", "DEBUG", "INFO", "WARN", "ERROR". */
  def name(level: LogLevel): String = level match {
    case LogLevel.Trace => "TRACE"
    case LogLevel.Debug => "DEBUG"
    case LogLevel.Info => "INFO"
    case LogLevel.Warn => "WARN"
    case LogLevel.Error => "ERROR"
  }

  /** The level a word names, whatever its case: the five's names, and WARNING, ERR, FATAL, SEVERE, CRITICAL and
    * NOTICE. */
  def parse(word: String): Option[LogLevel] = LogClassifier.named(word.toLowerCase)
}
