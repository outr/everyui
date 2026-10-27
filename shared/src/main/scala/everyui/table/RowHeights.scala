package everyui.table

/** How tall each of a long table's rows is, and where each starts, for a body that makes only the rows that show: a row
  * not yet drawn is taken to be `estimate` tall, and one drawn is as tall as it was measured. Where a row starts and
  * which row is at a height are found in steps of the logarithm of the count (a Fenwick tree of the measured rows'
  * differences from the estimate), so a million rows cost what a hundred do. */
private[table] class RowHeights(initialEstimate: Double) {
  private var estimateNow = initialEstimate
  private var n = 0
  // the differences from the estimate, a Fenwick tree over the rows (1-based), and each row's own, as measured
  private var tree: Array[Double] = new Array[Double](64)
  private var measured: Map[Int, Double] = Map.empty

  /** How tall a row not yet measured is taken to be. */
  def estimate: Double = estimateNow

  /** How many rows there are. */
  def count: Int = n

  /** Every measure let go, for `count` rows taken to be `estimate` tall. */
  def reset(count: Int, estimate: Double = estimateNow): Unit = {
    estimateNow = estimate
    n = math.max(0, count)
    // room for twice as many rows, so a table that grows a page at a time is not built again for each
    var room = 64
    while (room <= n * 2) room *= 2
    tree = new Array[Double](room)
    measured = Map.empty
  }

  /** As many rows as `count`, those measured and still among them kept. */
  def resize(count: Int): Unit = if (count != n) {
    if (count > tree.length - 1 || count < n) {
      val kept = measured.filter(_._1 < count)
      reset(count)
      kept.foreach { case (row, h) => measure(row, h) }
    }
    else n = count
  }

  /** Row `row` measured `height` tall: the difference it makes to every row after it. */
  def measure(row: Int, height: Double): Double = if (row < 0 || row >= n) 0.0 else {
    val change = height - heightOf(row)
    if (change != 0.0) {
      measured = measured.updated(row, height)
      var i = row + 1
      while (i < tree.length) {
        tree(i) += change
        i += i & -i
      }
    }
    change
  }

  /** How tall row `row` is: as measured, or the estimate. */
  def heightOf(row: Int): Double = measured.getOrElse(row, estimateNow)

  /** Whether row `row` has been measured. */
  def known(row: Int): Boolean = measured.contains(row)

  /** Where row `row` starts, from the first row's top. */
  def top(row: Int): Double = {
    val r = math.max(0, math.min(n, row))
    var sum = r * estimateNow
    var i = r
    while (i > 0) {
      sum += tree(i)
      i -= i & -i
    }
    sum
  }

  /** How tall the rows are together. */
  def total: Double = top(n)

  /** The row at height `y` from the first row's top: the first when above it, the last when below. */
  def rowAt(y: Double): Int = if (n == 0) 0 else {
    var low = 0
    var high = n - 1
    while (low < high) {
      val mid = (low + high + 1) / 2
      if (top(mid) <= y) low = mid else high = mid - 1
    }
    low
  }
}
