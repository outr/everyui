package everyui.push

/** How a push's body is written (`PushMessage.format`): Plain, shown as it is, or Markdown, styled where the platform
  * styles a notification's words and made plain words where it does not (`PushText`). */
enum PushFormat {
  case Plain, Markdown

  /** The word a push's data names it by ("format"): "plain" or "markdown". */
  def word: String = this match {
    case Plain => "plain"
    case Markdown => "markdown"
  }
}

object PushFormat {
  /** The format `word` names (`PushFormat.word`, in any case), Plain for any other word or none. */
  def named(word: String): PushFormat = if (word.trim.toLowerCase == "markdown") Markdown else Plain
}
