package everyui.chat

import everyui.Color
import everyui.component.{Component, Container, TextView}
import everyui.component.types.{TextDirection, WhiteSpace}
import everyui.theme.Theme
import reactify.*

/** A message's words as plain text: as wide as its longest line, wrapped at `widest`, its own line breaks kept. A
  * transcript shows them so where it is given `MessageText.plain` to (`Transcript.render`), in place of the Markdown
  * it shows by default (`MessageMarkdown`).
  *
  * The words are measured on one line first, out of sight, so a short reply makes a narrow bubble. They run the way
  * their own letters do (`direction`, `TextDirection.Auto` unless it is given), so an English message reads left to
  * right in an Arabic app and an Arabic one right to left in an English app, and a word too long for a line (a URL, a
  * path, a hash) is broken where it must be rather than running out of the bubble. */
class MessageText(text: Val[String], widest: Val[Double], points: Double = 14.0, colour: => Color = Theme.text(),
                  direction: TextDirection = TextDirection.Auto) extends Container {
  private val natural: TextView = new TextView {
    whiteSpace @= WhiteSpace.Pre
    font.size @= points
    content := text()
    visible @= false
  }
  val words: TextView = new TextView {
    whiteSpace @= WhiteSpace.PreWrap
    font.size @= points
    color := colour
    wrapAnywhere @= true
    content := text()
  }
  words.direction @= direction
  // as wide as the words on one line, once that has been read: before, the whole width (a line not yet read is 0 wide,
  // and the words were laid out a letter wide, read there, and laid out again)
  words.size.width := math.max(1.0,
    if (!natural.measured.known()) widest() else math.min(widest(), math.ceil(natural.size.width()) + 1.0))
  size.width := words.size.width()
  size.height := words.size.height()
  children ++= List(natural, words)

  override def kind: String = "MessageText"
}

object MessageText {
  /** A message's words shown plain, wrapped at the widest a bubble's text may be, not as Markdown. */
  def plain(text: Val[String], widest: Val[Double]): Component = new MessageText(text, widest)
}
