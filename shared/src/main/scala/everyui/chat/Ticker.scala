package everyui.chat

import everyui.render.Platform
import everyui.util.Clock
import reactify.*

/** The wall clock as a reactive value, moved on every `seconds`: what a live elapsed time and "5m ago" are bound to.
  * It runs until `stop`. */
class Ticker(seconds: Double = 1.0) {
  val now: Var[Long] = Var(Clock.real())
  private var running = true

  private def tick(): Unit = if (running) {
    now @= Clock.real()
    Platform.after(seconds, () => tick())
  }

  def stop(): Unit = running = false

  Platform.after(seconds, () => tick())
}
