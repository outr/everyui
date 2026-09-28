package everyui.keymap

import reactify.*

/** Something an app does from the keyboard: an `id` to find and rebind it by, a `label` for a list of shortcuts, the
  * chords that run it and what it does. `chords` starts at the defaults and can be rebound (and `reset`); `enabled`
  * turns it off without unbinding it, and can follow whatever it depends on (`enabled := editor.dirty()`). */
class Command(val id: String, val label: String, val defaults: List[Chord], action: () => Unit) {
  val chords: Var[List[Chord]] = Var(defaults)
  val enabled: Var[Boolean] = Var(true)
  /** Whether its chords run it even while the keys are an embed's (an editor in the page): an app's own Ctrl+Shift+N,
    * say. Off, the default, it runs only outside embeds, and inside one only on a chord the embed leaves to the app
    * (`Keymap.embed`). */
  val inEmbeds: Var[Boolean] = Var(false)

  /** Run it, as its chord or a menu entry does. */
  def run(): Unit = action()

  /** Back to the chords it was made with. */
  def reset(): Unit = chords @= defaults

  /** Whether `e` runs it: enabled, and one of its chords. */
  def runsOn(e: everyui.event.KeyEvent): Boolean = enabled() && chords().exists(_.matches(e))
}
