package everyui.focus

import reactify.*

/** Where text typed on a keyboard that is not a field of the platform's own lands: a Roku's characters from its phone
  * app, its voice entry and a remote with keys (ECP's `Lit_` keys), or a keyboard's keys where an app draws a keyboard
  * of its own over the words. A container states it (`Container.typeInto`), and while the focus is on it or in it what
  * is typed goes here, rather than to the focused part's `event.key.down`. */
trait TextEntry {
  /** `s` typed: a character, or a space. */
  def typed(s: String): Unit

  /** Backspace. */
  def erase(): Unit

  /** Enter typed (a phone app's Enter or Search, not the remote's OK, which still presses what has the focus). */
  def enter(): Unit
}

object TextEntry {
  /** Characters at the end of `text`, Backspace taking off the last; Enter runs `done`. */
  def apply(text: Var[String], done: () => Unit = () => ()): TextEntry = new TextEntry {
    def typed(s: String): Unit = text @= text() + s
    def erase(): Unit = if (text().nonEmpty) text @= text().substring(0, text().length - 1)
    def enter(): Unit = done()
  }
}
