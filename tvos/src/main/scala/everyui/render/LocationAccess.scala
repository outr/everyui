package everyui.render

import everyui.Permission
import everyui.location.{LocationAnswer, Position, Visit, VisitEvent, VisitOptions, Visits}
import everyui.tvos.{uiLocationAsk, uiLocationCurrent, uiLocationLast, uiLocationStatus, uiLocationUnwatch,
  uiLocationWatch, uiVisitsStart, uiVisitsStop}

/** Where the device is (`everyui.location.Location`) through Core Location on an iPhone or an iPad: the leave asked
  * for while the app is in use, or always (with the app closed: a visit wakes it), as the Info.plist's
  * NSLocationWhenInUseUsageDescription and NSLocationAlwaysAndWhenInUseUsageDescription say why (sbt-everyui writes
  * them from `everyuiLocation`). Visits are the system's (CLVisit), told as they wake the app, so `Visits.resume`
  * listens again as the app registers its `BackgroundApp`. An Apple TV gives an app no location of its own: there
  * `supported` is false, the permission refused, and a fix or a watch answers at once as unsupported. */
object LocationAccess {
  private val askedAlways = "everyui.asked.locationAlways"

  def supported(): Boolean = uiLocationStatus() >= 0

  def permission(done: Permission => Unit): Unit = done(uiLocationStatus() match {
    case 0 => Permission.NotAsked
    case 2 | 3 => Permission.Granted
    case _ => Permission.Refused
  })

  def permit(done: Boolean => Unit): Unit = uiLocationStatus() match {
    case 0 => uiLocationAsk(false, status => done(status >= 2))
    case 2 | 3 => done(true)
    case _ => done(false)
  }

  /** Always: not asked while iOS would still show its prompt for it (once, from while in use). */
  def backgroundPermission(done: Permission => Unit): Unit = done(uiLocationStatus() match {
    case 3 => Permission.Granted
    case 0 => Permission.NotAsked
    case 2 => if (Platform.setting(askedAlways).isEmpty) Permission.NotAsked else Permission.Refused
    case _ => Permission.Refused
  })

  def permitBackground(done: Boolean => Unit): Unit = uiLocationStatus() match {
    case 3 => done(true)
    case 0 | 2 =>
      Platform.saveSetting(askedAlways, "1")
      uiLocationAsk(true, status => done(status == 3))
    case _ => done(false)
  }

  def lastKnown(done: Option[Position] => Unit): Unit =
    done(if (uiLocationStatus() >= 2) position(uiLocationLast()) else None)

  def current(precise: Boolean, timeout: Double, done: LocationAnswer => Unit): Unit =
    if (!supported()) done(LocationAnswer.LocationUnsupported)
    else permit { granted =>
      if (!granted) done(LocationAnswer.LocationDenied)
      else uiLocationCurrent(precise, timeout, said => done(answer(said)))
    }

  def watch(precise: Boolean, distance: Double, answer: LocationAnswer => Unit): () => Unit =
    if (!supported()) {
      answer(LocationAnswer.LocationUnsupported)
      () => ()
    }
    else {
      var id = -1
      var stopped = false
      permit { granted =>
        if (!granted) answer(LocationAnswer.LocationDenied)
        else if (!stopped) id = uiLocationWatch(precise, distance, said => answer(this.answer(said)))
      }
      () => {
        stopped = true
        if (id >= 0) uiLocationUnwatch(id)
        id = -1
      }
    }

  def visitsSupported(): Boolean = supported()

  /** The system's visits, once the location is allowed always; `options` are the system's own judgement here. */
  def visitsStart(options: VisitOptions): Boolean =
    if (uiLocationStatus() != 3) {
      Platform.log("visits: not started: the location is not allowed always (Location.permitBackground)")
      false
    }
    else uiVisitsStart(said => visit(said).foreach(e => Visits.told(List(e)).start()))

  def visitsStop(): Unit = uiVisitsStop()

  def visitsCheck(delay: Double): Unit = ()

  private def answer(said: String): LocationAnswer =
    if (said == "D") LocationAnswer.LocationDenied
    else if (said == "U") LocationAnswer.LocationUnavailable
    else position(said) match {
      case Some(p) => LocationAnswer.Located(p)
      case None => LocationAnswer.LocationFailed(if (said.startsWith("F,")) said.substring(2) else said)
    }

  /** "L,lat,lon,accuracy,altitude,heading,speed,time", an unknown one empty. */
  private def position(said: String): Option[Position] = {
    val parts = said.split(',')
    val known = (s: String) => if (s.isEmpty) None else Some(s.toDouble)
    if (parts.length != 8 || parts(0) != "L") None
    else Some(Position(parts(1).toDouble, parts(2).toDouble, parts(3).toDouble, known(parts(4)), known(parts(5)),
      known(parts(6)), parts(7).toDouble.toLong))
  }

  /** "A" or "D", then ",lat,lon,accuracy,arrived,departed", departed -1 where it has not. */
  private def visit(said: String): Option[VisitEvent] = {
    val parts = said.split(',')
    if (parts.length != 6) None
    else {
      val departed = parts(5).toDouble.toLong
      val v = Visit(parts(1).toDouble, parts(2).toDouble, parts(3).toDouble, parts(4).toDouble.toLong,
        if (departed < 0L) None else Some(departed))
      if (parts(0) == "A") Some(VisitEvent.Arrived(v)) else Some(VisitEvent.Departed(v))
    }
  }
}
