package universal

import reactify.{Mutable, Stateful}
import universal.async.Task

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

/** youi's task DSL: `forever(sequential(x `to` 1.0 `in` 5.seconds `easing` e, sleep(2.seconds)))`. */
package object task {
  implicit def f2Task[T](f: => Task[T]): Animation = Action(f.unit)

  implicit class StateChannelWorkflowDouble(state: Stateful[Double] & Mutable[Double]) {
    def to(destination: => Double): PartialAnimate = PartialAnimate(
      get = () => state(),
      apply = (d: Double) => state := d,
      destination = () => destination
    )
  }

  def parallel(tasks: Animation*): Parallel = new Parallel(tasks.toList)
  def sequential(tasks: Animation*): Sequential = new Sequential(tasks.toList)
  def sleep(duration: FiniteDuration): Sleep = new Sleep(duration)
  def asynchronous(f: => Task[Unit]): Action = Action(f)
  def synchronous(f: => Unit): Action = Action(Task(f))
  def repeat(task: Animation, times: Int = 1): Repeat = new Repeat(task, times)
  def forever(task: Animation): Repeat = new Repeat(task, Int.MaxValue)
}
