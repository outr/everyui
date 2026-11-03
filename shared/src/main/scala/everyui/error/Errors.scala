package everyui.error

import everyui.render.Platform

/** Every error in the app, told to whatever listens (`onProblem`): what nothing caught, as the platform hears it
  * (`Platform.onUncaught`: the window's `error` and `unhandledrejection` on the web, a thread's uncaught exception on
  * the desktop and Android, a Roku's own report of a handler's throw), and what EveryUI catches where it calls the
  * app's code, with where that was: an event on a component (a click, a key, the pointer), and a frame (a task, an
  * animation). A listener's change of a value whose listeners throw is caught where EveryUI set it off, so a binding
  * that throws on a click is that click's. A rapid Task of the app's own is told with `everyui.network.Reported`.
  *
  * Until something listens nothing is caught that was not before: an event's throw goes where it went (the platform's
  * console, Android's crash). Once one does, an event that throws is told and the app goes on, as a web page does.
  * A listener that throws is not told of it, and a problem met while telling one is not told again.
  *
  * {{{
  *   Errors.onProblem(p => myLog.error(p.where + ": " + p.message, p.stack))
  * }}} */
object Errors {
  @volatile private var listeners: List[Problem => Unit] = Nil
  @volatile private var hearing = false
  @volatile private var telling = false

  /** `f` told every problem from now on; returns what stops it. */
  def onProblem(f: Problem => Unit): () => Unit = {
    if (!hearing) {
      hearing = true
      Platform.onUncaught((message, stack) => tell(Problem("uncaught", message, stack)))
    }
    listeners = listeners :+ f
    () => listeners = listeners.filterNot(_ eq f)
  }

  /** Whether anything listens. */
  def listening: Boolean = listeners.nonEmpty

  /** `t`, thrown `where`, told: to the platform's log, and to what listens. */
  def report(where: String, t: Throwable): Unit = {
    Platform.logError(where, t)
    tell(Problem(where, t.toString, Platform.stackOf(t)))
  }

  /** A problem with no throwable, told as it is. */
  def report(where: String, message: String, stack: String = ""): Unit = tell(Problem(where, message, stack))

  /** `f`, run where EveryUI calls the app's code: a throw from it told as `where`'s, `otherwise` in its place, once
    * something listens; before that it is thrown on as it was. */
  def guard[T](where: => String, otherwise: => T)(f: => T): T =
    if (listeners.isEmpty) f
    else try f catch {
      case t: Throwable =>
        report(where, t)
        otherwise
    }

  private def tell(problem: Problem): Unit = if (!telling) {
    telling = true
    try listeners.foreach(f => try f(problem) catch { case _: Throwable => () })
    finally telling = false
  }
}
