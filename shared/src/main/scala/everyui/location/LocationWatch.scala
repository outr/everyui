package everyui.location

/** The device's position followed, from [[Location.watch]]: each position that has moved at least `distance` metres
  * from the last one handed on (the first always) goes to `heard`, with what the platform says of it otherwise
  * (unavailable for a while, refused). [[stop]] ends it, and nothing is heard after. */
final class LocationWatch private[location] (distance: Double, heard: LocationAnswer => Unit) {
  private var over: Boolean = false
  private var halt: () => Unit = () => ()
  private var last: Option[Position] = None

  /** Whether it is still following. */
  def running: Boolean = !over

  /** Stops following: the platform stops looking (for this watch), and nothing more is heard. */
  def stop(): Unit = if (!over) {
    over = true
    halt()
  }

  private[location] def begin(stop: () => Unit): Unit =
    if (over) stop() else halt = stop

  private[location] def answer(a: LocationAnswer): Unit = if (!over) a match {
    case LocationAnswer.Located(p) =>
      if (last.forall(l => l.distanceTo(p) >= distance)) {
        last = Some(p)
        heard(a)
      }
    case LocationAnswer.LocationDenied | LocationAnswer.LocationUnsupported =>
      stop()
      heard(a)
    case _ => heard(a)
  }
}
