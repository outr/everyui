package everyui.monaco

/** The cursor moved (`MonacoController.onDidChangeCursor`): where it is, the other cursors' places, and what moved it.
  *
  * `source` is Monaco's: "mouse" (a click), "keyboard" (typing, the arrows, Home, select all), the id of the command
  * that edited (Backspace's "deleteLeft"), "api" (the app's own `setPosition` or `setSelection`), "modelChange" (the
  * text changed around it: the app's `setValue` or `executeEdits`, another editor's edit on a shared model, a copy
  * relayed into this one, and this editor's undo and redo, whose `reason` says so). A jump within the file (go to
  * definition, Ctrl+Click, a usage opened from a peek) is "code.jump", as VS Code names one: the bridge makes it
  * itself, at once, where the standalone editor would make it later as "api". `reason` says the rest
  * (MonacoCursorReason). */
case class MonacoCursorChange(position: MonacoPosition, secondaryPositions: List[MonacoPosition], source: String,
                              reason: MonacoCursorReason) {
  /** Moved by the person at the editor: typed, clicked, a key or a command of the editor's, a jump, an undo. Not by
    * the app, nor by an edit made elsewhere. */
  def byUser: Boolean = MonacoCursorChange.byUser(source, reason)
  /** A jump within the file: go to definition, Ctrl+Click, a usage opened from a peek. */
  def isJump: Boolean = source == MonacoCursorChange.Jump
}

object MonacoCursorChange {
  val Keyboard: String = "keyboard"
  val Mouse: String = "mouse"
  val Api: String = "api"
  val ModelChange: String = "modelChange"
  val Jump: String = "code.jump"

  /** Whether a move of `source` for `reason` was the user's. */
  def byUser(source: String, reason: MonacoCursorReason): Boolean =
    source != Api && reason != MonacoCursorReason.RecoverFromMarkers && reason != MonacoCursorReason.ContentFlush
}
