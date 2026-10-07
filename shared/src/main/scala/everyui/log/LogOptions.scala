package everyui.log

import everyui.PlatformKind
import everyui.render.Platform

/** How a `LogView` starts: how many lines it keeps before it lets the oldest go (`maxLines`), whether long lines wrap
  * or scroll sideways, whether each line shows its number, its time and its source, the size of its words, whether a
  * line naming no level takes the level of the one before it (`continuations`: a stack trace under its error), and
  * whether it starts following the newest line. Each is a `Var` of the view's after that. */
case class LogOptions(maxLines: Int = LogOptions.defaultLines,
                      wrap: Boolean = false,
                      lineNumbers: Boolean = false,
                      timestamps: Boolean = false,
                      sources: Boolean = false,
                      points: Double = 13.0,
                      continuations: Boolean = true,
                      follow: Boolean = true)

object LogOptions {
  /** 50,000 lines; 5,000 on a Roku, whose memory and speed are a phone's of long ago. */
  def defaultLines: Int = if (Platform.kind == PlatformKind.Roku) 5000 else 50000
}
