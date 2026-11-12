package everyui.render

/** Counts and times by key, for the idle audit: `top` the costliest (the most often, where nothing is timed) first. */
final class Tally {
  private val counts = new java.util.HashMap[String, Array[Long]]

  def add(key: String, nanos: Long): Unit = {
    // not computeIfAbsent, which is Android 7's
    var entry = counts.get(key)
    if (entry == null) {
      entry = new Array[Long](2)
      counts.put(key, entry)
    }
    entry(0) += 1L
    entry(1) += nanos
  }

  def nonEmpty: Boolean = !counts.isEmpty

  def clear(): Unit = counts.clear()

  /** The first `n`, each as `key xcount` and the milliseconds it took where it was timed. */
  def top(n: Int): String = {
    val all = scala.jdk.CollectionConverters.MapHasAsScala(counts).asScala.toList
    all.sortBy { case (_, e) => (-e(1), -e(0)) }.take(n).map { case (key, e) =>
      key + " x" + e(0).toString + (if (e(1) > 0L) " " + (e(1) / 1000000L).toString + "ms" else "")
    }.mkString("; ")
  }
}
