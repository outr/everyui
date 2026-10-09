package everyui.monaco

/** Why the cursor or the selection moved, as Monaco says: its `CursorChangeReason`. What moved it is the change's
  * `source` (MonacoCursorChange). */
enum MonacoCursorReason(val wire: Int) {
  /** Typing, a command's edit (Backspace), a jump, the app's own `setPosition`. */
  case NotSet extends MonacoCursorReason(0)
  /** The whole text was replaced (`setValue` without keeping the undo, a model loaded). */
  case ContentFlush extends MonacoCursorReason(1)
  /** The text changed under the cursor, which kept its place among the edits: an edit made elsewhere (the app's own
    * `setValue` or `executeEdits`, another editor on a shared model, a copy relayed from another web view). Never the
    * user's move. */
  case RecoverFromMarkers extends MonacoCursorReason(2)
  /** Moved to a place: a click, an arrow, Home, select all. */
  case Explicit extends MonacoCursorReason(3)
  case Paste extends MonacoCursorReason(4)
  /** This editor's undo and redo (its source "modelChange"). */
  case Undo extends MonacoCursorReason(5)
  case Redo extends MonacoCursorReason(6)
}

object MonacoCursorReason {
  def fromWire(value: Int): MonacoCursorReason = values.find(_.wire == value).getOrElse(NotSet)
}
