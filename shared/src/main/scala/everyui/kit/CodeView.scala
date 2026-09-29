package everyui.kit

import everyui.component.RichText
import everyui.component.types.WhiteSpace
import everyui.theme.Theme
import reactify.*

/** Code to read, not to edit: `text` in a monospaced face, its lines kept as they are, scrolled with the arrows, and
  * coloured for `language` where one is given (`Highlight`). What an editor embed shows where no web engine can run
  * one (a Roku, an Apple TV). */
class CodeView(text: => String, points: Double = 14.0, language: String = "") extends TextScroll {
  val code: RichText = new RichText {
    font.family @= "Roboto Mono"
    font.size @= points
    color := Theme.text()
    whiteSpace @= WhiteSpace.Pre
    runs := Highlight.runs(text, language)
  }
  content.children += code
  backgroundColor := Theme.surface()

  override def kind: String = "CodeView"
}
