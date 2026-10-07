package everyui.log

/** A line of a `LogView`: its words as they read (`text`, without escapes) and the `spans` of them in a style of their
  * own, its `level`, and what an event said of it: its place in the server's numbering (`seq`, 0 for none), when it
  * was written (`at`, milliseconds since the epoch, 0 for unknown) and where it came from (`source`). A `marker` is
  * the view's own note between lines (lines a reconnect missed), shown whatever the filter. `id` is the view's,
  * rising down the lines. */
case class LogLine(id: Long,
                   text: String,
                   spans: List[LogSpan] = Nil,
                   level: Option[LogLevel] = None,
                   seq: Long = 0L,
                   at: Long = 0L,
                   source: String = "",
                   marker: Boolean = false,
                   givenLevel: Boolean = false)
