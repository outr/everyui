package everyui.dialog

import everyui.async.Task

/** A dialog's answer, as a Task: what a button chose is kept as it is pressed, and told once the dialog has closed and
  * the focus is back, with `fallback` where nothing was chosen (Back, a click outside). */
private[dialog] class Reply[T] {
  private var value: Option[T] = None
  private var told = false
  private var waiting: List[T => Unit] = Nil

  /** The answer, unless one was given already. */
  def set(v: T): Unit = if (value.isEmpty) value = Some(v)

  /** The dialog closed: the answer is told, `fallback` where there was none. */
  def finish(fallback: T): Unit = if (!told) {
    set(fallback)
    told = true
    val v = value.get
    val all = waiting
    waiting = Nil
    all.foreach(f => f(v))
  }

  val task: Task[T] = Task.async[T](f => if (told) f(value.get) else waiting = waiting :+ f)
}
