package everyui.kit

import everyui.component.TextView
import everyui.component.types.WhiteSpace
import everyui.theme.Theme
import reactify.*

/** Code to read, not to edit: `text` in a monospaced face, its lines kept as they are, scrolled with the arrows. What
  * an editor embed shows where no web engine can run one (a Roku, an Apple TV). */
class CodeView(text: => String, points: Double = 14.0) extends TextScroll {
  val code: TextView = new TextView {
    font.family @= "Roboto Mono"
    font.size @= points
    color := Theme.text()
    whiteSpace @= WhiteSpace.Pre
    content := text
  }
  content.children += code
  backgroundColor := Theme.surface()

  override def kind: String = "CodeView"
}
