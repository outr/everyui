package everyui.render

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

/** `Platform.after` on the desktop: a timer on a thread of its own that hands `f` to the FX thread when it is due. A
  * JavaFX animation (a PauseTransition) would do the waiting on the FX thread, but a running one has JavaFX pulse every
  * frame until it ends, so every timer pending (a toast's, the clock's next second) kept an idle app drawing. */
object Timers {
  private lazy val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r => {
    val thread = new Thread(r, "everyui-timers")
    thread.setDaemon(true)
    thread
  })

  /** `f` on the FX thread once `seconds` have passed: on a later turn of it, even for none. */
  def after(seconds: Double, f: () => Unit): Unit =
    if (seconds <= 0.0) javafx.application.Platform.runLater(() => f())
    else scheduler.schedule((() => javafx.application.Platform.runLater(() => f())): Runnable,
      math.round(seconds * 1000000.0), TimeUnit.MICROSECONDS)
}
