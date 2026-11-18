package everyui.chart

import everyui.component.Component
import everyui.easing.Easing
import everyui.motion.ReduceMotion
import everyui.task.{PartialAnimate, TaskInstance, sequential, synchronous}
import reactify.*

import scala.concurrent.duration.*

/** A chart's values as they are drawn (`now`), easing to new ones over `Eased.seconds` as its data changes, so a bar
  * grows to its new height rather than jumping. They jump where people asked for less motion (`ReduceMotion`), where
  * the device is at its least quality, where no frames run, the first time, and where the data changes shape (a series
  * or a category more or fewer), as there is nothing to ease from. */
final class Eased(owner: Component) {
  val now: Var[Vector[Vector[Double]]] = owner.own(Var(Vector.empty))
  private var run: Option[TaskInstance] = None
  owner.onDispose(() => stop())

  /** Toward `next`. */
  def to(next: Vector[Vector[Double]]): Unit = {
    val from = now()
    stop()
    val same = from.nonEmpty && from.size == next.size && from.indices.forall(i => from(i).size == next(i).size)
    if (!same || ReduceMotion() || !everyui.perf.Quality.glides) now @= next
    else {
      val step = PartialAnimate(() => 0.0, (t: Double) => now @= Eased.between(from, next, t), () => 1.0)
        .in(math.round(Eased.seconds * 1000.0).millis).easing(Easing.cubicOut)
      val going = sequential(step, synchronous(now @= next)).start()
      // where the app runs no frame loop, nothing would step it: there at once
      if (!everyui.task.Frames.running) {
        going.cancel()
        now @= next
      }
      else run = Some(going)
    }
  }

  private def stop(): Unit = {
    run.foreach(_.cancel())
    run = None
  }
}

object Eased {
  /** How long a change takes. */
  val seconds: Double = 0.35

  /** The values `t` of the way from `a` to `b`, which have the same shape. */
  def between(a: Vector[Vector[Double]], b: Vector[Vector[Double]], t: Double): Vector[Vector[Double]] =
    a.indices.toVector.map(i => a(i).indices.toVector.map(j => a(i)(j) + (b(i)(j) - a(i)(j)) * t))
}
