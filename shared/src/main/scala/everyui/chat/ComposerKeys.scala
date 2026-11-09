package everyui.chat

import everyui.event.{Key, KeyEvent}
import everyui.text.CompletionKey

/** How a [[Composer]]'s field takes its keys, as a rule of its own so it can be read and checked apart from any field.
  *
  *  - A list of completions, while it shows, has Up and Down (to move through it), Enter and Tab (to take the lit one)
  *    and Escape (to close it), as any field's list of completions has (`CompletionKey`).
  *  - A suggestion, while it shows, is taken by Tab or Right (with no modifier) and put away by Escape. Tab with no
  *    suggestion showing moves the focus on as it always does.
  *  - Enter sends, and Shift+Enter types a new line. Where the keyboard is on the screen (`touch`) Enter types a new
  *    line too, and the Send button sends. Primary+Enter sends everywhere.
  *  - Enter that ends an input method's composition (`composing`) is the input method's: it never sends.
  *  - While a reply is coming (`busy`) or the composer is out of use, Enter is kept and does nothing: the words stay
  *    as they are to send when the reply is done, and no stray new line is typed into them. Where the composer queues
  *    messages while a reply comes (`queueing`), Enter sends there as it does at rest, and the composer queues it.
  *  - Up with the caret at the field's very start, where a queue shows above it (`queued`), goes to the queue.
  *  - Nothing happens while the field is not being typed in (a remote's focus only lighting it). */
object ComposerKeys {
  def action(e: KeyEvent, state: ComposerState, composing: Boolean = false): ComposerAction = {
    val plain = !e.ctrl && !e.meta && !e.alt && !e.shift
    val primary = (e.ctrl || e.meta) && !e.alt && !e.shift
    if (!state.editing || composing) ComposerAction.Pass
    else if (state.completing && CompletionKey.of(e).nonEmpty) CompletionKey.of(e).get match {
      case CompletionKey.Next => ComposerAction.Next
      case CompletionKey.Previous => ComposerAction.Previous
      case CompletionKey.Choose => ComposerAction.Complete
      case CompletionKey.Close => ComposerAction.Close
    }
    else if (state.suggesting && plain && (e.key == Key.Tab || e.key == Key.ArrowRight)) ComposerAction.Accept
    else if (state.suggesting && plain && e.key == Key.Escape) ComposerAction.Dismiss
    else if (state.queued && state.atStart && plain && e.key == Key.ArrowUp) ComposerAction.ToQueue
    else if (e.key != Key.Enter || e.shift || e.alt || (state.touch && !primary) || (!plain && !primary)) ComposerAction.Pass
    else if (!state.enabled || (state.busy && !state.queueing) || !state.sendable) ComposerAction.Hold
    else ComposerAction.Send
  }
}
