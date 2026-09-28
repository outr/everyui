package everyui.monaco.gallery

import everyui.component.{Container, TextView}
import everyui.theme.Theme
import reactify.*

/** The newest lines said, newest first, on a strip under an editor. */
class EventLog(title: String, waiting: String) extends Container {
  private val lines = Var[List[String]](Nil)
  private val heading = TextView(title, 12.0, Theme.muted())
  private val text = new TextView {
    font.family @= "Roboto Mono"
    font.size @= 12.0
    color := Theme.text()
    whiteSpace @= everyui.component.types.WhiteSpace.Pre
    content := (if (lines().isEmpty) waiting else lines().mkString("\n"))
  }

  backgroundColor := Theme.raised()
  heading.position.left @= 16.0
  heading.position.top @= 8.0
  text.position.left @= 16.0
  text.position.top @= 28.0
  children ++= List(heading, text)

  def say(line: String): Unit = lines @= (line :: lines()).take(5)
}
