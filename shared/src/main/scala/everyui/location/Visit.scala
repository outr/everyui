package everyui.location

/** A place the device stayed at (`Visits`): its middle (`latitude`, `longitude`, in degrees) and how sure of it the
  * platform is (`accuracy`, metres), when it arrived and, once it has gone, when it was last there (`departed`), each in
  * milliseconds since 1970. */
case class Visit(latitude: Double, longitude: Double, accuracy: Double, arrived: Long, departed: Option[Long])
