package everyui.call

import reactify.*

/** How loud the microphone hears the room, for a "check your microphone" step before a call (`MediaDevices.meter`):
  * `level` from 0 to 1 as it changes, until `stop`. Where the microphone cannot be had, `refused` says why and the
  * level stays at 0. */
class MicMeter private[call] () {
  private val heard: Var[Double] = Var(0.0)
  private val why: Var[Option[MediaRefusal]] = Var(None)
  private var stopping: () => Unit = () => ()
  private var stopped = false

  /** How loud, from 0 (silence) to 1. */
  val level: Val[Double] = heard
  /** Why the microphone could not be had, once it could not. */
  val refused: Val[Option[MediaRefusal]] = why

  /** The microphone let go. */
  def stop(): Unit = if (!stopped) {
    stopped = true
    heard @= 0.0
    stopping()
  }

  private[call] def begin(stop: () => Unit): Unit = if (stopped) stop() else stopping = stop
  private[call] def hear(l: Double): Unit = if (!stopped) heard @= math.max(0.0, math.min(1.0, l))
  private[call] def refuse(r: MediaRefusal): Unit = why @= Some(r)
}
