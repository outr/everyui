package everyui.text

import everyui.event.{Key, KeyEvent}

/** What a key does to a list of completions while it shows, the field it was opened from keeping the focus and the
  * caret: shared by a field's completions (`CompletionSource`) and the [[everyui.chat.Composer]]'s. */
sealed trait CompletionKey

object CompletionKey {
  /** The next one lit, round from the last to the first. */
  case object Next extends CompletionKey
  /** The one before lit, round from the first to the last. */
  case object Previous extends CompletionKey
  /** The lit one chosen. */
  case object Choose extends CompletionKey
  /** The list closed, and only the list: nothing else hears the key (a dialog the field is in stays). */
  case object Close extends CompletionKey

  /** What `e` does to a list that shows, pressed with no modifier: Down and Up move the lit row, Enter and Tab choose
    * it, Escape closes the list. Any other key is the field's (Home and End move its caret, letters type). */
  def of(e: KeyEvent): Option[CompletionKey] = {
    val plain = !e.ctrl && !e.meta && !e.alt && !e.shift
    if (!plain) None
    else if (e.key == Key.ArrowDown) Some(Next)
    else if (e.key == Key.ArrowUp) Some(Previous)
    else if (e.key == Key.Enter || e.key == Key.Tab) Some(Choose)
    else if (e.key == Key.Escape) Some(Close)
    else None
  }

  /** Whether `e` removes the lit row of a field's list, where it has an action that removes it
    * (`everyui.chat.CompletionAction.removes`): Shift+Delete, as a browser's own suggestions are removed (on a Mac's
    * keyboard Shift+Fn+Delete). Not one of the keys above, as the composer's list has no such actions. */
  def removes(e: KeyEvent): Boolean = e.shift && !e.ctrl && !e.meta && !e.alt && e.name == "Delete"

  /** The row lit after `lit` among `count`, `forward` or back, going round. */
  def step(lit: Int, count: Int, forward: Boolean): Int =
    if (count <= 0) 0 else if (forward) (lit + 1) % count else (lit + count - 1) % count
}
