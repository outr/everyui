package everyui.chat

import everyui.component.types.PasteAction

/** What a paste into a [[Composer]] becomes (`Composer.pasted`): words in the field, as any field's paste
  * (`AsText`, with `Insert`, `InsertText` and `Ignore` for short), or a file going with the message (`Attach`), a
  * chip exactly as a dropped file is, taken where the composer's `accept`, `allow` and `maxSize` agree.
  *
  * {{{
  *   // a log or a stack trace becomes a file rather than flooding the message
  *   composer.pasted @= Some(ComposerPaste.longAsFile(1500))
  * }}} */
sealed trait ComposerPaste

object ComposerPaste {
  /** The paste as a field takes it: landing, other words in its place, or nothing. */
  case class AsText(action: PasteAction) extends ComposerPaste

  /** The paste a file going with the message, not words in the field. Refused (by `accept`, `allow` or `maxSize`), it
    * is refused as a dropped file is, with a word saying why, and nothing lands. */
  case class Attach(attachment: Attachment) extends ComposerPaste

  /** The paste lands as it would with no hook. */
  val Insert: ComposerPaste = AsText(PasteAction.Insert)

  /** Nothing lands. */
  val Ignore: ComposerPaste = AsText(PasteAction.Ignore)

  /** `text` lands in the paste's place. */
  def InsertText(text: String): ComposerPaste = AsText(PasteAction.InsertText(text))

  /** A paste longer than `over` characters (a log, a stack trace, a whole file) as a file named after its first line
    * (`Attachment.pasted`); a shorter one lands. */
  def longAsFile(over: Int): String => ComposerPaste =
    text => if (text.length > over) Attach(Attachment.pasted(text)) else Insert
}
