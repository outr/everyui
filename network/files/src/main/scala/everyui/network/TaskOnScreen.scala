package everyui.network

import everyui.render.Platform
import rapid.Task

/** `task`'s answer handed to `f` on the thread that draws, and the task answering with what `f` gives back, or failing
  * as it fails. For the step that changes components with what came back: on the JVM (the desktop, Android) a task
  * goes on, after a socket's answer or a `Task.sleep`, on a thread of rapid's own, where a change to what is drawn has
  * to be taken to the thread that draws (EveryUI does, and logs where with `everyui.hops`); this makes it there.
  * {{{
  *   import everyui.network.onScreen
  *
  *   server.ask(Calendar.Month(at)).onScreen(month => days.children @= month.days.map(DayCell(_))).startUnit()
  * }}}
  * Where the task already is on the thread that draws (the web's one thread, or a task run by `sync()` there), `f`
  * runs at once; elsewhere on the thread's next turn (`Platform.later`), and what follows `onScreen` in the task goes
  * on on rapid's thread again. */
extension [T](task: Task[T]) {
  def onScreen[R](f: T => R): Task[R] = task.flatMap { value =>
    if (Platform.onDrawingThread()) Task(f(value))
    else {
      val done = Task.completable[R]
      Platform.later(() =>
        try done.success(f(value))
        catch { case t: Throwable => done.failure(t) })
      done
    }
  }
}
