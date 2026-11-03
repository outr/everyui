package everyui.network

import everyui.error.Errors
import rapid.Task

/** For a Task's `handleError`: its failure told to what hears the app's problems (`Errors`) as `where`'s, and
  * `fallback` in its place, so a Task started with nothing to hear how it ends still reports how it failed.
  *
  * {{{
  *   load(id).handleError(Reported("load " + id, Nil)).map(show).startUnit()
  * }}} */
object Reported {
  def apply[T](where: String, fallback: => T): Throwable => Task[T] = t => {
    Errors.report(where, t)
    Task(fallback)
  }
}
