package everyui.monaco

/** Monaco's `KeyMod` flags, or'ed with a MonacoKeyCode into a keybinding: `MonacoKeyMod.CtrlCmd | MonacoKeyCode.KeyS`.
  * `CtrlCmd` is Command on a Mac and Ctrl elsewhere; `WinCtrl` is Ctrl everywhere. */
object MonacoKeyMod {
  val CtrlCmd: Int = 2048
  val Shift: Int = 1024
  val Alt: Int = 512
  val WinCtrl: Int = 256
}
