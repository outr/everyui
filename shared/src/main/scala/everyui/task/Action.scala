package everyui.task

import everyui.async.Task

class Action(action: () => Task[Unit]) extends Animation:
  private var running = false
  private var finished = false

  override def update(delta: Double, reset: Boolean): Conclusion =
    if reset then
      finished = false
      running = false
    if !finished && !running then
      running = true
      action().map { _ =>
        finished = true
        running = false
      }.startUnit()
      Conclusion.Continue
    else if !finished then Conclusion.Continue
    else Conclusion.Finished

object Action:
  def apply(f: => Task[Unit]): Action = new Action(() => f)
