package universal.task

import scala.annotation.tailrec

class Parallel(tasks: List[Animation]) extends Animation:
  private var running: List[Animation] = Nil

  override def update(delta: Double, reset: Boolean): Conclusion =
    if reset then running = tasks
    recursive(running, delta, reset)
    if running.isEmpty then Conclusion.Finished else Conclusion.Continue

  @tailrec
  private def recursive(tasks: List[Animation], delta: Double, reset: Boolean): Unit =
    if tasks.isEmpty then ()
    else
      val task = tasks.head
      task.update(delta, reset) match
        case Conclusion.Continue => // keep going
        case Conclusion.Finished => running = running.filterNot(_ == task)
      recursive(tasks.tail, delta, reset)
