package everyui.monaco

import fabric.rw.*

/** A 1-based place in the text: Monaco's `IPosition`. Column 1 is before the first character. */
case class MonacoPosition(line: Int, column: Int) derives RW {
  override def toString: String = s"MonacoPosition($line:$column)"
}
