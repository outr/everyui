package everyui.location

/** What the platform said of where the device is (`Location.current`, `Location.watch`). Its cases' names are their
  * own, since a Roku channel's type names are global. */
sealed trait LocationAnswer

object LocationAnswer {
  /** Where it is. */
  case class Located(position: Position) extends LocationAnswer
  /** The person (or the platform's policy) refused the location. A watch ends with it. */
  case object LocationDenied extends LocationAnswer
  /** No position to be had just now: no signal, the location turned off in the settings, or none found in time. A watch
    * goes on, and may hear one later. */
  case object LocationUnavailable extends LocationAnswer
  /** No location on this platform (the desktop, a Roku, an Apple TV). A watch ends with it. */
  case object LocationUnsupported extends LocationAnswer
  /** Anything else, as the platform put it. */
  case class LocationFailed(message: String) extends LocationAnswer
}
