package everyui.task

import reactify.Channel

/** youi's `Updates`: a per-frame `delta` (seconds) for anything that listens, plus the running tasks.
  *
  * youi attaches each running task to `delta` as a reaction. Here the tasks are stepped directly, in the order
  * they were started, from the same `update`: the same frame, the same order, and the same snapshot rules (a task
  * that finishes or starts during a frame does not change which tasks that frame steps), without a reactive fire
  * per task per frame.
  *
  * Frames run only while something needs them (`Frames`): a task started wakes them, and they run while one moves
  * or something listens to `delta`. */
trait Updates {
  lazy val delta: Channel[Double] = Channel[Double]

  private var running: Vector[TaskInstance] = Vector.empty

  def update(delta: Double): Unit = {
    this.delta @= delta
    val tasks = running
    if (tasks.nonEmpty) tasks.foreach(_.advance(delta))
  }

  def updateTasks(): Boolean = true

  /** Whether the next frame must run for this: while a task moves (one paused does not), or something listens to
    * `delta`. What does work of its own in `update` every frame (a screen counting down) says so here, for as long as
    * it does, or its frames stop when nothing else needs them (`Frames`). */
  def needsFrames: Boolean = running.exists(_.moving) || delta.reactions().nonEmpty

  private[task] def addTask(instance: TaskInstance): Unit = {
    running = running :+ instance
    Frames.wake()
  }

  private[task] def removeTask(instance: TaskInstance): Unit = running = running.filterNot(_ eq instance)
}
