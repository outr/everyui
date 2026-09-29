package everyui.render

import everyui.Permission
import everyui.location.{LocationAnswer, Position}
import org.scalajs.dom

import scala.scalajs.js

/** Where the device is (`everyui.location.Location`) through `navigator.geolocation`, which asks the person the first
  * time (the site's "location" permission) and answers only on a page served over HTTPS or from `localhost`. A watch
  * is `watchPosition`, which has no distance of its own: `LocationWatch` keeps back what has not moved far enough. */
object LocationAccess {
  private def geolocation: js.Dynamic = dom.window.navigator.asInstanceOf[js.Dynamic].geolocation

  def supported(): Boolean = dom.window.asInstanceOf[js.Dynamic].isSecureContext.asInstanceOf[Boolean] &&
    !js.isUndefined(geolocation)

  /** The site's "geolocation" permission, where the browser says (the Permissions API); without it, not asked. */
  def permission(done: Permission => Unit): Unit = {
    val permissions = dom.window.navigator.asInstanceOf[js.Dynamic].permissions
    if (!supported()) done(Permission.Refused)
    else if (js.isUndefined(permissions)) done(Permission.NotAsked)
    else permissions.query(js.Dynamic.literal(name = "geolocation")).asInstanceOf[js.Promise[js.Dynamic]]
      .`then`[Unit] { (status: js.Dynamic) =>
        done(String.valueOf(status.state) match {
          case "granted" => Permission.Granted
          case "denied" => Permission.Refused
          case _ => Permission.NotAsked
        })
      }
      .`catch`[Unit]((_: Any) => done(Permission.NotAsked))
  }

  /** The browser asks as a position is first asked for: one is, any age will do, and only a refusal is a no. */
  def permit(done: Boolean => Unit): Unit =
    if (!supported()) done(false)
    else geolocation.getCurrentPosition((_: js.Dynamic) => done(true),
      (e: js.Dynamic) => done(e.code.asInstanceOf[Int] != 1),
      js.Dynamic.literal(maximumAge = Double.PositiveInfinity, timeout = 30000))

  def current(precise: Boolean, timeout: Double, done: LocationAnswer => Unit): Unit =
    if (!supported()) done(LocationAnswer.LocationUnsupported)
    else geolocation.getCurrentPosition((p: js.Dynamic) => done(located(p)), (e: js.Dynamic) => done(failed(e)),
      js.Dynamic.literal(enableHighAccuracy = precise, timeout = timeout * 1000.0, maximumAge = 0))

  def watch(precise: Boolean, distance: Double, answer: LocationAnswer => Unit): () => Unit =
    if (!supported()) {
      answer(LocationAnswer.LocationUnsupported)
      () => ()
    }
    else {
      val id = geolocation.watchPosition((p: js.Dynamic) => answer(located(p)), (e: js.Dynamic) => answer(failed(e)),
        js.Dynamic.literal(enableHighAccuracy = precise, maximumAge = 0))
      () => geolocation.clearWatch(id)
    }

  private def located(p: js.Dynamic): LocationAnswer = {
    val c = p.coords
    val known = (v: js.Dynamic) =>
      if (v == null || js.isUndefined(v) || v.asInstanceOf[Double].isNaN) None else Some(v.asInstanceOf[Double])
    LocationAnswer.Located(Position(c.latitude.asInstanceOf[Double], c.longitude.asInstanceOf[Double],
      c.accuracy.asInstanceOf[Double], known(c.altitude), known(c.heading), known(c.speed), p.timestamp.asInstanceOf[Double].toLong))
  }

  /** A GeolocationPositionError: 1 refused, 2 no position, 3 none in time. */
  private def failed(e: js.Dynamic): LocationAnswer = e.code.asInstanceOf[Int] match {
    case 1 => LocationAnswer.LocationDenied
    case 2 | 3 => LocationAnswer.LocationUnavailable
    case _ => LocationAnswer.LocationFailed(String.valueOf(e.message))
  }
}
