package everyui.list

/** How many cells a row of a `VirtualGrid` holds: `count` of them (`GridColumns.fixed`), or as many as fit at
  * `minWidth` each (`GridColumns.adaptive`), and always at least one. The cells share the width the gaps leave. */
case class GridColumns(count: Int, minWidth: Double) {
  /** How many cells a row `width` wide holds, `gap` apart. */
  def across(width: Double, gap: Double): Int =
    if (count > 0) count else math.max(1, ((width + gap) / (math.max(1.0, minWidth) + gap)).toInt)

  /** How wide each cell of a row `width` wide is, `gap` apart. */
  def cellWidth(width: Double, gap: Double): Double = {
    val n = across(width, gap)
    math.max(0.0, (width - gap * (n - 1)) / n)
  }
}

object GridColumns {
  /** `count` cells a row, however wide the grid is. */
  def fixed(count: Int): GridColumns = GridColumns(math.max(1, count), 0.0)
  /** As many cells a row as fit at `minWidth` each: more as the grid widens. */
  def adaptive(minWidth: Double): GridColumns = GridColumns(0, minWidth)
}
