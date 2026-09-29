package everyui.component.types

/** What a paste into a text field becomes, as the app's `pasted` hook answers for the words on the clipboard
  * (`InputView.pasted`, `TextArea.pasted`, `TextInput.pasted`): let them land, put other words in their place, or
  * nothing at all. Words put in their place go where the paste would have, at the caret or over the selection, and are
  * undone as the paste would have been.
  *
  * {{{
  *   // pasted into a phone number field: only its digits
  *   phone.pasted @= Some(text => PasteAction.InsertText(text.filter(_.isDigit)))
  * }}} */
sealed trait PasteAction

object PasteAction {
  /** The paste lands as it would with no hook: the default. */
  case object Insert extends PasteAction

  /** `text` lands in the paste's place, at the caret or over the selection. */
  case class InsertText(text: String) extends PasteAction

  /** Nothing lands: the app took the words somewhere else, or would not have them. */
  case object Ignore extends PasteAction
}
