package everyui.location

/** What makes a visit (`Visits.start`): the device kept within `radius` metres of one place for at least `minMinutes`.
  * Apple's visits (CLVisit) are the system's own judgement and use neither. */
case class VisitOptions(minMinutes: Int = 10, radius: Double = 100.0) {
  /** As kept in the app's settings. */
  def written: String = minMinutes.toString + "," + radius.toString
}

object VisitOptions {
  /** Options as `written` kept them, or None. */
  def read(text: String): Option[VisitOptions] = {
    val parts = text.split(',')
    if (parts.length != 2) None
    else try Some(VisitOptions(parts(0).toInt, parts(1).toDouble))
    catch { case _: NumberFormatException => None }
  }
}
