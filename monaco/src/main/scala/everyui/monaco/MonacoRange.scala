package everyui.monaco

import fabric.rw.*

/** A 1-based range of the text, `[start, end)`: Monaco's `IRange`. */
case class MonacoRange(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) derives RW {
  def start: MonacoPosition = MonacoPosition(startLine, startColumn)
  def end: MonacoPosition = MonacoPosition(endLine, endColumn)
  def isEmpty: Boolean = startLine == endLine && startColumn == endColumn
  override def toString: String = s"MonacoRange($startLine:$startColumn-$endLine:$endColumn)"
}

object MonacoRange {
  /** Nothing selected at `position`. */
  def collapsed(position: MonacoPosition): MonacoRange =
    MonacoRange(position.line, position.column, position.line, position.column)

  /** Whole lines, `from` to `to` inclusive: what a line-range highlight covers. */
  def lines(from: Int, to: Int): MonacoRange = MonacoRange(from, 1, to, 1)
}
