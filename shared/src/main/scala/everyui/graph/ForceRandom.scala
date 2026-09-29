package everyui.graph

/** A force layout's own numbers, the same for the same seed on every platform: Park and Miller's generator, worked by
  * Schrage's method so it never overflows 32 bits (a Roku's integers, and Swift, which traps an overflow). */
private[graph] class ForceRandom(seed: Int) {
  private var state: Int = {
    val s = seed % 2147483647
    val positive = if (s < 0) s + 2147483647 else s
    if (positive == 0) 1 else positive
  }

  /** The next number, from 0 up to 1. */
  def next(): Double = {
    val hi = state / 127773
    val lo = state % 127773
    val t = 16807 * lo - 2836 * hi
    state = if (t > 0) t else t + 2147483647
    (state - 1).toDouble / 2147483646.0
  }

  /** A nudge too small to see, for two nodes in the same place, so they have a way to part. */
  def jiggle(): Double = (next() - 0.5) * 1.0e-6
}
