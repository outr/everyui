package everyui.chat

/** What a key does in a [[Composer]]'s field (`ComposerKeys.action`). */
sealed trait ComposerAction

object ComposerAction {
  /** Nothing of the composer's: the key goes on as it would in any field (typed, or the focus layer's). */
  case object Pass extends ComposerAction
  /** Kept, and nothing done: Enter while a reply is coming (unless it queues), or while the composer is out of use. */
  case object Hold extends ComposerAction
  /** The draft sent, or queued while a reply comes. */
  case object Send extends ComposerAction
  /** The suggestion taken into the field. */
  case object Accept extends ComposerAction
  /** The suggestion put away for this turn. */
  case object Dismiss extends ComposerAction
  /** The next completion in the list lit. */
  case object Next extends ComposerAction
  /** The one before it lit. */
  case object Previous extends ComposerAction
  /** The lit completion put in place of the word. */
  case object Complete extends ComposerAction
  /** The completions closed. */
  case object Close extends ComposerAction
  /** The focus taken up to the queue above the field, onto its last message. */
  case object ToQueue extends ComposerAction
}
