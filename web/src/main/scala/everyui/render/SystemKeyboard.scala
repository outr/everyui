package everyui.render

import everyui.event.{Key, Keys}

/** The system's keyboard on the screen, as a TV's runtime says it shows and hides (`AppHost.hearKeyboard`), and the
  * app's field that has the page's focus while it does. On webOS the keyboard comes up as a field takes the page's
  * focus, and goes down by itself (Back, an arrow off its edge, a USB keyboard's first key) with the field still
  * focused: the typing is over, but the field is not let go, since LG asks that it not be on the keyboard going down
  * (its voice button takes the keyboard down and up again). The next key the remote sends there lets it go first,
  * so it goes on from the field as from any component: an arrow moves the focus, OK starts the typing again (and the
  * keyboard with it), Back goes back. A character or Backspace is a keyboard's own, typing on: the field is kept.
  * Plain Scala, so the desktop's specs read it as the TV does. */
class SystemKeyboard {
  // the keyboard went down by itself with a field focused, and nothing has been typed since
  private var down = false

  /** The keyboard shown or hidden, `inField` whether an app's field has the page's focus. */
  def shown(visible: Boolean, inField: Boolean): Unit = down = !visible && inField

  /** A key pressed, by its web name, `inField` whether an app's field has the page's focus: whether the field is to be
    * let go before the key is heard. */
  def letsGo(name: String, inField: Boolean): Boolean =
    if (!inField) {
      down = false
      false
    }
    else if (down && SystemKeyboard.leaves(name)) {
      down = false
      true
    }
    else {
      if (SystemKeyboard.types(name)) down = false
      false
    }
}

object SystemKeyboard {
  /** The remote's keys that go on from a field once its keyboard is down: the arrows, OK and Back. */
  def leaves(name: String): Boolean = {
    val key = Keys.of(name)
    Keys.isArrow(key) || key == Key.Enter || key == Key.Back
  }

  /** A key that types (a character, Backspace, Delete): a keyboard of its own typing on. */
  def types(name: String): Boolean = name.codePointCount(0, name.length) == 1 || name == "Backspace" || name == "Delete"
}
