package everyui.chat

/** The word being typed at the caret when it starts with a trigger: `trigger`, where the word starts in the message
  * (`start`, the trigger's own place), and where it ends (`end`, past the caret to the next space). `prefix` is what
  * has been typed after the trigger, up to the caret. */
case class TriggerWord(trigger: Char, start: Int, end: Int, prefix: String)

object TriggerWord {
  private def space(c: Char): Boolean = c == ' ' || c == '\n' || c == '\t' || c == '\r'

  /** The word at `caret` in `text` when it starts with one of `triggers`: after a space or at the start of the message,
    * or for a trigger `atStart`, with nothing but spaces before it. */
  def at(text: String, caret: Int, triggers: List[CompletionTrigger]): Option[TriggerWord] =
    if (caret < 1 || caret > text.length) None
    else {
      var start = caret
      while (start > 0 && !space(text.charAt(start - 1))) start -= 1
      if (start >= caret) None
      else {
        val c = text.charAt(start)
        val found = triggers.find(t => t.char == c && (!t.atStart || text.substring(0, start).trim.isEmpty))
        if (found.isEmpty) None
        else {
          var end = caret
          while (end < text.length && !space(text.charAt(end))) end += 1
          Some(TriggerWord(c, start, end, text.substring(start + 1, caret)))
        }
      }
    }

  /** `text` with `word` replaced by `insert` and a space after it (unless one follows already), and where the caret goes:
    * just past that space. */
  def replace(text: String, word: TriggerWord, insert: String): (String, Int) = {
    val rest = text.substring(word.end)
    val spaced = if (rest.nonEmpty && space(rest.charAt(0))) insert else insert + " "
    val before = text.substring(0, word.start) + spaced
    val caret = if (spaced.length == insert.length) before.length + 1 else before.length
    (before + rest, caret)
  }
}
