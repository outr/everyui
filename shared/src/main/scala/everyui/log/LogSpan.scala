package everyui.log

/** A stretch of a line's text, from `from` to `to` (excluded), drawn in `style`, and a link to `href` where it is one
  * (OSC 8). What is in no span is the view's own style. */
case class LogSpan(from: Int, to: Int, style: AnsiStyle, href: String = "")
