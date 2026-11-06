package everyui.time

import everyui.render.Platform
import reactify.*

/** How much longer than an app's own time people are to be given to read something or act on it: what they asked the
  * device for (Android's "Time to take action"), 1 elsewhere, or what the app sets (its own setting). A toast stays
  * that much longer (`everyui.kit.Toast`), and so does a time limit's warning (`TimeLimit`). */
object TimeLimits {
  lazy val factor: Var[Double] = Var(Platform.timeScale())

  /** `seconds` as long as people are to be given: never shorter. */
  def scaled(seconds: Double): Double = seconds * math.max(1.0, factor())
}
