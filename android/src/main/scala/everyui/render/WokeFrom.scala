package everyui.render

/** Where the frame loop was woken from (`Frames.origin`): the thread, and its stack as it was, printed only if a frame
  * asked for then never comes. Kept rather than printed, as the loop wakes many times a minute. */
class WokeFrom {
  private val thread = Thread.currentThread.getName
  private val stack = new Throwable

  override def toString: String = "thread " + thread + ": " + stack.getStackTrace.iterator.map(_.toString)
    .filterNot(s => s.startsWith("everyui.task.Frames") || s.startsWith("everyui.render.WokeFrom"))
    .take(12).mkString(" < ")
}
