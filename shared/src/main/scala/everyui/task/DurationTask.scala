package everyui.task

import scala.concurrent.duration.FiniteDuration

trait DurationTask extends Animation {
  private var elapsed: Double = 0.0

  def time: FiniteDuration
  def act(delta: Double, elapsed: Double, progress: Double, reset: Boolean): Unit

  override def update(delta: Double, reset: Boolean): Conclusion = {
    if (reset) elapsed = 0.0
    elapsed += delta
    val duration = time.toMillis / 1000.0
    // a task of no length is over the moment it starts: dividing by it is a runtime error on a Roku, and a NaN
    // everywhere else, which the eased value then carried into whatever it was moving
    val progress = if (duration <= 0.0) 1.0 else math.min(elapsed / duration, 1.0)
    act(delta, elapsed, progress, reset)
    if (elapsed >= duration) Conclusion.Finished else Conclusion.Continue
  }
}
