package everyui.monaco

/** The selection changed (`MonacoController.onDidChangeSelection`): the primary one, the other cursors', and what
  * changed it, as `MonacoCursorChange` says. */
case class MonacoSelectionChange(selection: MonacoSelection, secondarySelections: List[MonacoSelection], source: String,
                                 reason: MonacoCursorReason) {
  def byUser: Boolean = MonacoCursorChange.byUser(source, reason)
  def isJump: Boolean = source == MonacoCursorChange.Jump
}
