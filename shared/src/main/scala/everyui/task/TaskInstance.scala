package everyui.task

case class TaskInstance(task: Animation, updates: Updates) {
  private var first = true
  private var elapsed: Double = 0.0
  private var paused: Boolean = false
  private var step: Double = 0.0

  private[task] def advance(delta: Double): Unit =
    if (!paused && updates.updateTasks()) {
      elapsed += delta
      if (step >= task.stepSize) {
        step = 0.0
        task.update(delta, first) match {
          case Conclusion.Continue => // Keep going
          case Conclusion.Finished => updates.removeTask(this)
        }
        first = false
      }
    }

  def start(): Unit = updates.addTask(this)

  def pause(): Unit = paused = true
  def play(): Unit = paused = false

  def cancel(): Unit = updates.removeTask(this)
}
