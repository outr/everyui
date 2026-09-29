package everyui.keymap

import everyui.event.KeyEvent
import everyui.render.Platform

/** Follows the keys for a double-tap of a modifier on its own: pressed and released twice, the second release within
  * `Keymap.doubleTapWindow` of the first press, with no other key going down between (Ctrl+C then Ctrl is not one)
  * and no other modifier held. A key held down repeating is not a press. On a remote, where no modifier is ever
  * pressed on its own, there is none. */
class DoubleTap {
  // the modifier being tapped ("" for none), how often it has gone down and come up, and when it first went down
  private var modifier = ""
  private var downs = 0
  private var ups = 0
  private var started = 0.0

  /** Hear `e`, pressed or released, repeating or not: the modifier whose double-tap this release completes, else "". */
  def key(e: KeyEvent, pressed: Boolean, repeat: Boolean): String =
    if (repeat) ""
    else if (pressed) {
      down(e)
      ""
    }
    else up(e)

  private def down(e: KeyEvent): Unit = {
    val m = DoubleTap.modifierOf(e.name)
    if (m.isEmpty || !DoubleTap.alone(m, e)) modifier = ""
    else if (m == modifier && downs == 1 && ups == 1 && within()) downs = 2
    else if (Platform.remoteNavigation()) modifier = ""
    else {
      modifier = m
      downs = 1
      ups = 0
      started = Platform.ticks()
    }
  }

  private def up(e: KeyEvent): String =
    if (modifier.isEmpty || DoubleTap.modifierOf(e.name) != modifier || ups >= downs) ""
    else if (!within()) {
      modifier = ""
      ""
    }
    else {
      ups += 1
      if (ups < 2) ""
      else {
        val m = modifier
        modifier = ""
        m
      }
    }

  private def within(): Boolean = Platform.ticks() - started <= Keymap.doubleTapWindow()
}

object DoubleTap {
  /** The modifier a key is, by the web's name ("Control", "Shift", "Alt", "Meta"), "" for any other key. */
  def modifierOf(name: String): String =
    if (name == "Control" || name == "Shift" || name == "Alt" || name == "Meta") name
    else if (name == "OS") "Meta"
    else ""

  /** Whether modifier `m` is pressed with no other modifier held. */
  def alone(m: String, e: KeyEvent): Boolean =
    (m == "Control" || !e.ctrl) && (m == "Meta" || !e.meta) && (m == "Alt" || !e.alt) && (m == "Shift" || !e.shift)
}
