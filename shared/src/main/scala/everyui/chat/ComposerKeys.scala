package everyui.chat

import everyui.event.{Key, KeyEvent}

/** How a [[Composer]]'s field takes its keys, as a rule of its own so it can be read and checked apart from any field.
  *
  *  - A list of completions, while it shows, has Up and Down (to move through it), Enter and Tab (to take the lit one)
  *    and Escape (to close it).
  *  - A suggestion, while it shows, is taken by Tab or Right (with no modifier) and put away by Escape. Tab with no
  *    suggestion showing moves the focus on as it always does.
  *  - Enter sends, and Shift+Enter types a new line. Where the keyboard is on the screen (`touch`) Enter types a new
  *    line too, and the Send button sends. Primary+Enter sends everywhere.
  *  - Enter that ends an input method's composition (`composing`) is the input method's: it never sends.
  *  - While a reply is coming (`busy`) or the composer is out of use, Enter is kept and does nothing: the words stay
  *    as they are to send when the reply is done, and no stray new line is typed into them.
  *  - Nothing happens while the field is not being typed in (a remote's focus only lighting it). */
object ComposerKeys {
  def action(e: KeyEvent, state: ComposerState, composing: Boolean = false): ComposerAction = {
    val plain = !e.ctrl && !e.meta && !e.alt && !e.shift
    val primary = (e.ctrl || e.meta) && !e.alt && !e.shift
    if (!state.editing || composing) ComposerAction.Pass
    else if (state.completing && plain && e.key == Key.ArrowDown) ComposerAction.Next
    else if (state.completing && plain && e.key == Key.ArrowUp) ComposerAction.Previous
    else if (state.completing && plain && (e.key == Key.Enter || e.key == Key.Tab)) ComposerAction.Complete
    else if (state.completing && plain && e.key == Key.Escape) ComposerAction.Close
    else if (state.suggesting && plain && (e.key == Key.Tab || e.key == Key.ArrowRight)) ComposerAction.Accept
    else if (state.suggesting && plain && e.key == Key.Escape) ComposerAction.Dismiss
    else if (e.key != Key.Enter || e.shift || e.alt || (state.touch && !primary) || (!plain && !primary)) ComposerAction.Pass
    else if (!state.enabled || state.busy || !state.sendable) ComposerAction.Hold
    else ComposerAction.Send
  }
}
