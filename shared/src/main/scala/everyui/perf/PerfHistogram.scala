package everyui.perf

/** Times in milliseconds counted into fixed buckets, so taking one is a few comparisons and a percentile wants no
  * sorting and no list of every sample: what a frame can afford on a Roku. A percentile is read between its bucket's
  * bounds, as far in as its rank falls, and never outside the shortest and the longest seen. */
class PerfHistogram {
  private val counts: Array[Int] = new Array[Int](PerfHistogram.bounds.length + 1)
  private var n: Int = 0
  private var most: Double = 0.0
  private var least: Double = 0.0

  /** How many were taken, and the longest. */
  def count: Int = n
  def max: Double = most

  def add(ms: Double): Unit = {
    val bounds = PerfHistogram.bounds
    var lo = 0
    var hi = bounds.length
    while (lo < hi) {
      val mid = (lo + hi) / 2
      if (ms <= bounds(mid)) hi = mid else lo = mid + 1
    }
    counts(lo) = counts(lo) + 1
    if (n == 0 || ms < least) least = ms
    n += 1
    if (ms > most) most = ms
  }

  /** Everything `other` took, as though taken here. */
  def addAll(other: PerfHistogram): Unit = {
    var i = 0
    while (i < counts.length) {
      counts(i) = counts(i) + other.counts(i)
      i += 1
    }
    if (other.n > 0 && (n == 0 || other.least < least)) least = other.least
    n += other.n
    if (other.most > most) most = other.most
  }

  /** What `share` (0 to 1) of them took at most; -1 with none taken. */
  def percentile(share: Double): Double = if (n == 0) -1.0 else {
    val bounds = PerfHistogram.bounds
    val rank = share * n.toDouble
    var seen = 0
    var i = 0
    while (i < counts.length - 1 && seen.toDouble + counts(i).toDouble < rank) {
      seen += counts(i)
      i += 1
    }
    val low = math.max(least, if (i == 0) 0.0 else bounds(i - 1))
    val high = if (i < bounds.length) math.min(bounds(i), most) else most
    val within = if (counts(i) == 0) 1.0 else math.max(0.0, math.min(1.0, (rank - seen.toDouble) / counts(i).toDouble))
    math.max(low, low + (high - low) * within)
  }

  def clear(): Unit = {
    var i = 0
    while (i < counts.length) {
      counts(i) = 0
      i += 1
    }
    n = 0
    most = 0.0
    least = 0.0
  }
}

object PerfHistogram {
  /** Each bucket's upper bound: close together where a frame's time is told apart (16 against 33), wider beyond. */
  val bounds: Array[Double] = Array(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 17.0, 20.0, 25.0, 30.0, 34.0, 40.0, 50.0, 60.0,
    67.0, 80.0, 100.0, 125.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0, 600.0, 800.0, 1000.0, 1500.0, 2000.0, 3000.0,
    5000.0)
}
