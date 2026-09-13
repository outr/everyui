package universal.task

import reactify.Channel

/** youi's `Updates`: a per-frame `delta` (seconds) for anything that listens, plus the running tasks.
  *
  * youi attaches each running task to `delta` as a reaction. Here the tasks are stepped directly, in the order
  * they were started, from the same `update`: the same frame, the same order, and the same snapshot rules (a task
  * that finishes or starts during a frame does not change which tasks that frame steps), without a reactive fire
  * per task per frame. */
trait Updates:
  lazy val delta: Channel[Double] = Channel[Double]

  private var running: Vector[TaskInstance] = Vector.empty

  def update(delta: Double): Unit =
    this.delta @= delta
    val tasks = running
    if tasks.nonEmpty then tasks.foreach(_.advance(delta))

  def updateTasks(): Boolean = true

  private[task] def addTask(instance: TaskInstance): Unit = running = running :+ instance

  private[task] def removeTask(instance: TaskInstance): Unit = running = running.filterNot(_ eq instance)
