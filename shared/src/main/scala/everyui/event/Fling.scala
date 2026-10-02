package everyui.event

/** A finger's drag along one way, followed so that a flick carries on once it lifts, as a phone's own lists do: `at`
  * each place it moves to, then `velocity` as it lifts, which a `FlingRun` glides on from, slowing as a phone's does.
  * A finger that stopped before it lifted has none, and the content stays where it was let go. */
class Fling {
  // (when, where): the places the finger was at, newest last, no further back than Fling.window
  private var samples: Vector[(Double, Double)] = Vector.empty

  /** The finger is at `place` (pixels along the drag) at `ms` (`Platform.ticks()`). */
  def at(place: Double, ms: Double): Unit =
    samples = samples.filter(s => ms - s._1 <= Fling.window) :+ ((ms, place))

  /** How fast it was going, in pixels a millisecond along the drag, as it lifted at `ms`. */
  def velocity(ms: Double): Double = Fling.velocity(samples, ms)

  /** A drag starting again: what came before is forgotten. */
  def clear(): Unit = samples = Vector.empty
}

object Fling {
  /** How far back the finger's moves count, in milliseconds: its last tenth of a second, as phones take it. */
  val window: Double = 100.0
  /** A finger still for longer than this before it lifted had stopped: no fling. */
  val stopped: Double = 40.0
  /** How much of its speed a fling keeps each millisecond: iOS's normal deceleration, which Android's is close to. */
  val keep: Double = 0.998
  /** Slower than this, in pixels a millisecond, a lift is no flick at all, and a fling has come to rest. */
  val slowest: Double = 0.05
  val rest: Double = 0.02
  /** No fling goes faster, in pixels a millisecond: Android's fastest. */
  val fastest: Double = 8.0

  /** The velocity of `samples` (when, where) at `ms`: the slope through those of its last `window` by least squares,
    * which a jittery finger cannot throw as two points could; none where it stopped before `ms`, had too few, or
    * went too slowly to be a flick. */
  def velocity(samples: Vector[(Double, Double)], ms: Double): Double =
    if (samples.isEmpty || ms - samples.last._1 > stopped) 0.0
    else {
      val newest = samples.last._1
      val recent = samples.filter(s => newest - s._1 <= window)
      if (recent.size < 2) 0.0
      else {
        // sums taken by hand, not with `sum`, which a Roku's emitted code would look for a Numeric to do
        val n = recent.size
        var st = 0.0
        var sp = 0.0
        var i = 0
        while (i < n) {
          st += recent(i)._1
          sp += recent(i)._2
          i += 1
        }
        val mt = st / n.toDouble
        val mp = sp / n.toDouble
        var across = 0.0
        var along = 0.0
        i = 0
        while (i < n) {
          val dt = recent(i)._1 - mt
          across += dt * dt
          along += dt * (recent(i)._2 - mp)
          i += 1
        }
        val v = if (across <= 0.0) 0.0 else along / across
        if (math.abs(v) < slowest) 0.0 else math.max(-fastest, math.min(fastest, v))
      }
    }

  /** How long, in milliseconds, a fling's speed takes to fall by `e`: what its distance and its slowing are measured in. */
  val lasting: Double = -1.0 / math.log(keep)

  /** How far a fling at `v` (pixels a millisecond) goes over `ms`, and how fast it is going then. */
  def step(v: Double, ms: Double): (Double, Double) = {
    val left = math.pow(keep, ms)
    (v * lasting * (1.0 - left), v * left)
  }

  /** How far a fling at `v` goes before it comes to rest. */
  def distance(v: Double): Double = v * lasting
}
