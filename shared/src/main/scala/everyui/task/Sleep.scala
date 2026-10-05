package everyui.task

import scala.concurrent.duration.FiniteDuration

class Sleep(val time: FiniteDuration) extends DurationTask {
  override def paced: Boolean = false

  override def act(delta: Double, elapsed: Double, progress: Double, reset: Boolean): Unit = ()
}
