package everyui.text

/** The piece of a field being completed (`CompletionSource`): where it starts and ends in what the field holds, and
  * `text`, what has been typed of it up to the caret, which is what the source is asked about. */
case class CompletionToken(start: Int, end: Int, text: String)

object CompletionToken {
  private def space(c: Char): Boolean = c == ' ' || c == '\n' || c == '\t' || c == '\r'

  private def separator(c: Char, separators: String): Boolean = {
    var i = 0
    var found = false
    while (!found && i < separators.length) {
      found = separators.charAt(i) == c
      i += 1
    }
    found
  }

  /** The piece of `value` at `caret`: with no `separators`, the whole of it, its leading spaces left out; else what
    * lies between the separator before the caret and the one after it (or the field's ends), its leading spaces and its
    * trailing ones past the caret left out (all of them in the whole field), `text` being what of it comes before the
    * caret (all of it in the whole field, wherever the caret is). */
  def at(value: String, caret: Int, separators: String): CompletionToken = {
    val at = math.max(0, math.min(value.length, caret))
    var start = 0
    var end = value.length
    if (separators.nonEmpty) {
      start = at
      while (start > 0 && !separator(value.charAt(start - 1), separators)) start -= 1
      end = at
      while (end < value.length && !separator(value.charAt(end), separators)) end += 1
    }
    while (start < end && space(value.charAt(start))) start += 1
    while (end > start && (separators.isEmpty || end > at) && space(value.charAt(end - 1))) end -= 1
    val typed = if (separators.isEmpty) end else math.max(start, at)
    CompletionToken(start, end, value.substring(start, typed))
  }

  /** `value` with `token` replaced by `insert`, `after` following it unless what follows (past any spaces) is already
    * one of `separators`; and where the caret goes: just past what was put in. */
  def replace(value: String, token: CompletionToken, insert: String, after: String = "",
              separators: String = ""): (String, Int) = {
    val rest = value.substring(token.end)
    var next = 0
    while (next < rest.length && space(rest.charAt(next))) next += 1
    val separated = (rest.nonEmpty && separator(rest.charAt(0), separators)) ||
      (next < rest.length && separator(rest.charAt(next), separators))
    val put = if (separated) insert else insert + after
    val before = value.substring(0, token.start) + put
    (before + rest, before.length)
  }
}
