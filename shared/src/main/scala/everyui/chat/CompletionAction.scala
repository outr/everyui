package everyui.chat

import everyui.font.MaterialIcon

/** Something a row of a field's completions does besides being chosen (`Completion.actions`): `icon` drawn as a small
  * button at the row's end, `label` its tooltip and what a screen reader calls it ("Remove"), and `run` what it does,
  * without choosing the row, the field keeping the focus and the caret (and a phone its keyboard). The list stays
  * open and the source is asked again for the same piece, so what `run` changed shows. One that `removes` the
  * suggestion (a past entry forgotten) takes its row out of the list at once, and is what Shift+Delete does to the lit
  * row from the field, as a browser's own suggestions are removed. */
case class CompletionAction(icon: MaterialIcon, label: String, run: () => Unit, removes: Boolean = false)
