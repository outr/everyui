package universal.ui

/** youi's animation task DSL, ported.
  *
  * youi models a task as `trait Task { def update(delta, reset): Conclusion }`
  * with `Sequential`, `Parallel`, `Repeat`, `Sleep` and `AnimateIn` as
  * subclasses. Here a `Task` IS its update function and the combinators build
  * closures — youi's state machine line for line. (The emitter now dispatches
  * virtually, so the class hierarchy would also work; the closure form stayed
  * because it is smaller.) `Conclusion` becomes a Boolean `finished`.
  *
  * Per-task bookkeeping (elapsed time, the remaining sequence) is plain `var`s,
  * exactly as in youi's `DurationTask`. It is not reactive state and does not
  * go through reactify; the emitter boxes a `var` a closure captures, so each
  * frame sees the last frame's value.
  */
class Task(val step: (Double, Boolean) => Boolean):
  /** @param delta seconds since the previous frame
    * @param reset restart this task from its beginning
    * @return true when the task has finished */
  def update(delta: Double, reset: Boolean): Boolean = step(delta, reset)

object Task:
  /** youi's `AnimateIn`: ease `set` from wherever `get` reads at reset to
    * `destination`, over `seconds`. `destination` is by-function because youi
    * re-reads it each cycle (the screen may have resized). */
  def animate(get: () => Double,
              set: Double => Unit,
              destination: () => Double,
              seconds: Double,
              easing: Easing): Task =
    var elapsed = 0.0
    var from = 0.0
    new Task((delta, reset) =>
      if reset then
        elapsed = 0.0
        from = get()
      elapsed = elapsed + delta
      val progress = if elapsed / seconds > 1.0 then 1.0 else elapsed / seconds
      set(from + (destination() - from) * easing(progress))
      elapsed >= seconds
    )

  /** youi's `Sleep` — a DurationTask that does nothing but wait. */
  def sleep(seconds: Double): Task =
    var elapsed = 0.0
    new Task((delta, reset) =>
      if reset then elapsed = 0.0
      elapsed = elapsed + delta
      elapsed >= seconds
    )

  /** youi's `Action` in its synchronous form (`synchronous(f)`). */
  def sync(f: () => Unit): Task =
    new Task((_, _) =>
      f()
      true
    )

  /** youi's `Sequential`: run each task to completion in turn, resetting the
    * next one as it starts. */
  def sequential(tasks: List[Task]): Task =
    var remaining = tasks
    var resetNext = false
    new Task((delta, reset) =>
      if reset then
        remaining = tasks
        resetNext = false
      val resetNow = reset || resetNext
      resetNext = false
      if remaining.isEmpty then true
      else if remaining.head.update(delta, resetNow) then
        remaining = remaining.tail
        if remaining.isEmpty then true
        else
          resetNext = true
          false
      else false
    )

  /** youi's `Parallel`: step every task each frame, dropping each as it
    * finishes; done when all are. */
  def parallel(tasks: List[Task]): Task =
    var remaining = tasks
    new Task((delta, reset) =>
      if reset then remaining = tasks
      remaining = remaining.filterNot(t => t.update(delta, reset))
      remaining.isEmpty
    )

  /** youi's `forever(task)` — `Repeat` with `Int.MaxValue` times. */
  def forever(task: Task): Task =
    var resetNext = false
    new Task((delta, reset) =>
      val resetNow = reset || resetNext
      resetNext = false
      if task.update(delta, resetNow) then resetNext = true
      false
    )

/** youi's `TaskInstance` + `Updates`: the per-frame pump. youi drives it from
  * a reactify `delta` channel fed by `requestAnimationFrame`; here it is fed by
  * `Platform.animate`, which is `requestAnimationFrame` on the web and a
  * SceneGraph `Timer` on Roku. */
class Animator:
  private var tasks: List[Task] = Nil
  private var last: Double = 0.0

  def start(task: Task): Unit =
    var first = true
    val wrapped = new Task((delta, _) =>
      val reset = first
      first = false
      task.update(delta, reset)
    )
    tasks = tasks :+ wrapped

  /** Advance every running task by the elapsed wall-clock time. */
  def tick(now: Double): Unit =
    if last == 0.0 then last = now
    val delta = (now - last) / 1000.0
    last = now
    tasks.foreach(t => t.update(delta, false))
