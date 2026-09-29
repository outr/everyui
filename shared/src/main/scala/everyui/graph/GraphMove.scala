package everyui.graph

import everyui.easing.Easing
import everyui.task.DurationTask

import scala.concurrent.duration.*

/** A graph moving to its new layout over `seconds`: `step` told how far along it is (0 to 1, eased), each frame. */
private[graph] class GraphMove(seconds: Double, step: Double => Unit) extends DurationTask {
  override def time: FiniteDuration = (seconds * 1000.0).toLong.millis

  override def act(delta: Double, elapsed: Double, progress: Double, reset: Boolean): Unit =
    step(Easing.cubicInOut.calculate(progress))
}
