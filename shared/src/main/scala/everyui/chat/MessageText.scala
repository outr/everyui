package everyui.chat

import everyui.Color
import everyui.component.{Component, Container, TextView}
import everyui.component.types.WhiteSpace
import everyui.theme.Theme
import reactify.*

/** A message's words as plain text: as wide as its longest line, wrapped at `widest`, its own line breaks kept. What a
  * transcript shows a message with unless it is given another way to (`Transcript.render`: Markdown, say).
  *
  * The words are measured on one line first, out of sight, so a short reply makes a narrow bubble. */
class MessageText(text: Val[String], widest: Val[Double], points: Double = 14.0, colour: => Color = Theme.text())
  extends Container {
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
    content := text()
  }
  words.size.width := math.max(1.0, math.min(widest(), math.ceil(natural.size.width()) + 1.0))
  size.width := words.size.width()
  size.height := words.size.height()
  children ++= List(natural, words)

  override def kind: String = "MessageText"
}

object MessageText {
  /** The default way a transcript shows a message's words: plain, wrapped at the widest a bubble's text may be. */
  def plain(text: Val[String], widest: Val[Double]): Component = new MessageText(text, widest)
}
