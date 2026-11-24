package everyui.location

/** Where the device has stayed so far, as `Visits` judges it from the positions it hears where the platform has no
  * visits of its own (Android): the middle of the fixes heard within the radius (`latitude`, `longitude`), the best of
  * their accuracies, when the first and the last of them were fixed (`since`, `last`), how many there were, and whether
  * it has been there long enough to be a visit (`arrived`). */
case class Dwell(latitude: Double, longitude: Double, accuracy: Double, since: Long, last: Long, fixes: Int,
                 arrived: Boolean) {
  /** The visit it is, gone where `departed` says when. */
  def visit(departed: Option[Long]): Visit = Visit(latitude, longitude, accuracy, since, departed)

  /** As kept in the app's settings. */
  def written: String = List(latitude.toString, longitude.toString, accuracy.toString, since.toString, last.toString,
    fixes.toString, if (arrived) "1" else "0").mkString(",")
}

object Dwell {
  /** The dwell after `position`, and what it makes of the visit: a position fixed no later than the last one heard
    * changes nothing; one within `radius` of the middle moves it (a running mean) and is an arrival once `minMinutes`
    * have passed since the first; one beyond it is a departure from a visit arrived at, and the start of another stay.
    * A fix rougher than twice the radius cannot place the device within it: it is a departure where even its accuracy
    * puts it beyond the radius, and otherwise passed over. */
  def heard(options: VisitOptions, dwell: Option[Dwell], position: Position): (Option[Dwell], List[VisitEvent]) = {
    val rough = position.accuracy > options.radius * 2.0
    val begun =
      if (rough) None
      else Some(Dwell(position.latitude, position.longitude, position.accuracy, position.time, position.time, 1, false))
    dwell match {
      case None => (begun, Nil)
      case Some(d) if position.time <= d.last => (dwell, Nil)
      case Some(d) =>
        val distance = Position(d.latitude, d.longitude, d.accuracy, None, None, None, d.last).distanceTo(position)
        if (rough && distance <= options.radius + position.accuracy) (dwell, Nil)
        else if (!rough && distance <= options.radius) {
          val n = d.fixes + 1
          val moved = d.copy(latitude = d.latitude + (position.latitude - d.latitude) / n,
            longitude = d.longitude + (position.longitude - d.longitude) / n,
            accuracy = math.min(d.accuracy, position.accuracy), last = position.time, fixes = n)
          if (!d.arrived && moved.last - moved.since >= options.minMinutes.toLong * 60000L) {
            val arrived = moved.copy(arrived = true)
            (Some(arrived), List(VisitEvent.Arrived(arrived.visit(None))))
          }
          else (Some(moved), Nil)
        }
        else (begun, if (d.arrived) List(VisitEvent.Departed(d.visit(Some(d.last)))) else Nil)
    }
  }

  /** A dwell as `written` kept it, or None. */
  def read(text: String): Option[Dwell] = {
    val parts = text.split(',')
    if (parts.length != 7) None
    else try Some(Dwell(parts(0).toDouble, parts(1).toDouble, parts(2).toDouble, parts(3).toLong, parts(4).toLong,
      parts(5).toInt, parts(6) == "1"))
    catch { case _: NumberFormatException => None }
  }
}
