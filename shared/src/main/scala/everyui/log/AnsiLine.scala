package everyui.log

/** A line as a terminal would show it: its `text`, without escapes, tabs set out as spaces, and the `spans` of it
  * drawn in a style of their own, in order. */
case class AnsiLine(text: String, spans: List[LogSpan] = Nil)
