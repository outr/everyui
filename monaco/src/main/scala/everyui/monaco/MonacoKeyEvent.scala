package everyui.monaco

import fabric.rw.*

/** A key in the editor (`onKeyDown`, `onKeyUp`): the browser's `key` and `code`, Monaco's own `keyCode`
  * (MonacoKeyCode) and the modifiers. It cannot be stopped from here; bind a command or an action for that. */
case class MonacoKeyEvent(key: String = "", code: String = "", keyCode: Int = 0, ctrl: Boolean = false,
                          shift: Boolean = false, alt: Boolean = false, meta: Boolean = false) derives RW {
  override def toString: String = {
    val held = List(Option.when(ctrl)("Ctrl"), Option.when(shift)("Shift"), Option.when(alt)("Alt"), Option.when(meta)("Meta")).flatten
    if (held.isEmpty) s"MonacoKeyEvent($code)" else s"MonacoKeyEvent(${held.mkString("+")}+$code)"
  }
}
