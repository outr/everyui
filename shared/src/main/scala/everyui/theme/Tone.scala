package everyui.theme

import everyui.Color

/** One of the theme's roles a mark is drawn in (a highlighted passage, `everyui.chat.MessageHighlight`): its colour is
  * the palette's, so it follows the theme, light or dark. */
enum Tone {
  case Accent, Info, Success, Warning, Danger

  /** The role's own colour: a fill's, an edge's. */
  def colour(): Color = this match {
    case Accent => Theme.accent()
    case Info => Theme.info()
    case Success => Theme.success()
    case Warning => Theme.warning()
    case Danger => Theme.danger()
  }

  /** The role's colour as words are drawn in it, reading at 4.5 to 1 on every ground of the palette. */
  def text(): Color = this match {
    case Accent => Theme.accentText()
    case Info => Theme.infoText()
    case Success => Theme.successText()
    case Warning => Theme.warningText()
    case Danger => Theme.dangerText()
  }
}
