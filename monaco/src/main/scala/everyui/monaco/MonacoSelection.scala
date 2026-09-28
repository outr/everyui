package everyui.monaco

import fabric.rw.*

/** A selection with a direction: a range, plus where it was started from (the anchor) and where the cursor is (the
  * position). Monaco's `ISelection`. */
case class MonacoSelection(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
                           selectionStartLine: Int, selectionStartColumn: Int,
                           positionLine: Int, positionColumn: Int) derives RW {
  def range: MonacoRange = MonacoRange(startLine, startColumn, endLine, endColumn)
  def selectionStart: MonacoPosition = MonacoPosition(selectionStartLine, selectionStartColumn)
  def position: MonacoPosition = MonacoPosition(positionLine, positionColumn)
  def isEmpty: Boolean = range.isEmpty
  /** Whether it was made backwards: the cursor before the anchor. */
  def isReversed: Boolean =
    positionLine < selectionStartLine || (positionLine == selectionStartLine && positionColumn < selectionStartColumn)
}

object MonacoSelection {
  /** Nothing selected, the cursor at `position`. */
  def collapsed(position: MonacoPosition): MonacoSelection = at(position, position)

  /** From `anchor` to the cursor at `cursor`, in that direction. */
  def at(anchor: MonacoPosition, cursor: MonacoPosition): MonacoSelection = {
    val forward = anchor.line < cursor.line || (anchor.line == cursor.line && anchor.column <= cursor.column)
    val (start, end) = if (forward) (anchor, cursor) else (cursor, anchor)
    MonacoSelection(start.line, start.column, end.line, end.column, anchor.line, anchor.column, cursor.line, cursor.column)
  }

  /** `range` selected forwards. */
  def of(range: MonacoRange): MonacoSelection = at(range.start, range.end)
}
