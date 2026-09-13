package universal.task

import universal.ui

/** youi's `youi.task.Task`: one step of an animation per frame. Named `Animation` here because
  * `universal.async.Task` (youi's `rapid.Task`) takes the name `Task`. */
trait Animation:
  def update(delta: Double, reset: Boolean): Conclusion

  def stepSize: Double = 0.0

  def andThen(that: Animation): Animation = new Sequential(List(this, that))

  def start(taskSupport: TaskSupport = ui): TaskInstance = taskSupport.start(this)
