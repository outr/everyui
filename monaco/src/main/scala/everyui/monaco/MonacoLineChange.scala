package everyui.monaco

import fabric.*
import fabric.rw.*

/** A changed region of a diff, by lines (1-based, inclusive): the original's `originalStartLine` to `originalEndLine`
  * against the modified's. Lines added have no original lines (`originalEndLine` 0, after `originalStartLine`); lines
  * deleted have no modified lines (`modifiedEndLine` 0, after `modifiedStartLine`). `charChanges` are the characters
  * within, where Monaco worked them out. Monaco's `ILineChange`. */
case class MonacoLineChange(originalStartLine: Int, originalEndLine: Int, modifiedStartLine: Int, modifiedEndLine: Int,
                            charChanges: List[MonacoCharChange] = Nil) derives RW {
  /** Lines added, where the original had none. */
  def isAddition: Boolean = originalEndLine == 0
  /** Lines deleted, where the modified has none. */
  def isDeletion: Boolean = modifiedEndLine == 0
  /** The modified side's lines (none for a deletion). */
  def modifiedLines: Range.Inclusive = if (isDeletion) 1 to 0 else modifiedStartLine to modifiedEndLine
  /** The original side's lines (none for an addition). */
  def originalLines: Range.Inclusive = if (isAddition) 1 to 0 else originalStartLine to originalEndLine
  /** The modified side's line its gutter marks sit on: its first, or for a deletion the line it was deleted after. */
  def anchorLine: Int = if (isDeletion) math.max(1, modifiedStartLine) else modifiedStartLine
  /** Whether `line` of the modified side belongs to it (a deletion claims the line it sits on). */
  def holds(line: Int): Boolean = if (isDeletion) line == anchorLine else modifiedLines.contains(line)
  /** The modified side's range, whole lines. */
  def modifiedRange: MonacoRange =
    if (isDeletion) MonacoRange(anchorLine, 1, anchorLine, 1) else MonacoRange(modifiedStartLine, 1, modifiedEndLine, Int.MaxValue)
  /** The original side's range, whole lines. */
  def originalRange: MonacoRange =
    if (isAddition) MonacoRange(math.max(1, originalStartLine), 1, math.max(1, originalStartLine), 1)
    else MonacoRange(originalStartLine, 1, originalEndLine, Int.MaxValue)
}
