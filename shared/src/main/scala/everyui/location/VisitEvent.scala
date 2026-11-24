package everyui.location

/** What `Visits` tells the app's `BackgroundApp.onVisit`: the device has stayed at a place long enough to be a visit
  * (`Arrived`), or has left one it arrived at (`Departed`, with the time it was last there). */
sealed trait VisitEvent {
  def visit: Visit
}

object VisitEvent {
  case class Arrived(visit: Visit) extends VisitEvent
  case class Departed(visit: Visit) extends VisitEvent
}
