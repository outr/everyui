package everyui.render

import android.content.pm.PackageManager
import android.location.{LocationListener, LocationManager, Location => AndroidLocation}
import android.os.{Build, CancellationSignal, Handler, Looper}
import everyui.Permission
import everyui.location.{LocationAnswer, Position}

/** Where the device is (`everyui.location.Location`) through the LocationManager: the fused provider where the device
  * has one, satellites for a precise fix and the network's for a rough one otherwise. The manifest declares
  * ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION, asked for together, so the person chooses precise or approximate.
  * A watch passes its distance to the platform too, which then wakes the app less. Answers come on the thread that
  * draws. */
object LocationAccess {
  private val fine = "android.permission.ACCESS_FINE_LOCATION"
  private val coarse = "android.permission.ACCESS_COARSE_LOCATION"
  private lazy val main = new Handler(Looper.getMainLooper)

  private def manager: LocationManager = Host.activity.getSystemService(classOf[LocationManager])

  def supported(): Boolean = Host.activity.getPackageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

  private def held: Boolean = Permissions.held(fine) || Permissions.held(coarse)

  def permission(done: Permission => Unit): Unit =
    done(if (!supported()) Permission.Refused else if (held) Permission.Granted else Permissions.state(coarse))

  def permit(done: Boolean => Unit): Unit =
    if (!supported()) done(false) else Permissions.askAll(List(fine, coarse))(_ => done(held))

  def current(precise: Boolean, timeout: Double, done: LocationAnswer => Unit): Unit =
    allowed(done) { provider =>
      val cancel = new CancellationSignal
      var answered = false
      val once = (a: LocationAnswer) => if (!answered) {
        answered = true
        done(a)
      }
      main.postDelayed(() => if (!answered) {
        cancel.cancel()
        once(LocationAnswer.LocationUnavailable)
      }, (timeout * 1000.0).toLong)
      manager.getCurrentLocation(provider(precise), cancel, Host.activity.getMainExecutor,
        (l: AndroidLocation) => once(if (l == null) LocationAnswer.LocationUnavailable else located(l)))
    }

  def watch(precise: Boolean, distance: Double, answer: LocationAnswer => Unit): () => Unit = {
    var listener: Option[LocationListener] = None
    var stopped = false
    allowed(answer) { provider =>
      if (!stopped) {
        val heard: LocationListener = new LocationListener {
          override def onLocationChanged(l: AndroidLocation): Unit = answer(located(l))
          override def onProviderDisabled(name: String): Unit = answer(LocationAnswer.LocationUnavailable)
        }
        listener = Some(heard)
        manager.requestLocationUpdates(provider(precise), 1000L, distance.toFloat, Host.activity.getMainExecutor, heard)
      }
    }
    () => {
      stopped = true
      listener.foreach(l => manager.removeUpdates(l))
      listener = None
    }
  }

  /** `start` with the provider to ask, once the person has allowed a location (asking where they have not), and the
    * device has one turned on; otherwise `done` hears why not. */
  private def allowed(done: LocationAnswer => Unit)(start: (Boolean => String) => Unit): Unit =
    if (!supported()) done(LocationAnswer.LocationUnsupported)
    else permit { granted =>
      if (!granted) done(LocationAnswer.LocationDenied)
      else if (!manager.isLocationEnabled) done(LocationAnswer.LocationUnavailable)
      else {
        val on = (name: String) => manager.getProviders(true).contains(name)
        val fused = Build.VERSION.SDK_INT >= 31 && on(LocationManager.FUSED_PROVIDER)
        val pick = (precise: Boolean) =>
          if (fused) LocationManager.FUSED_PROVIDER
          else if (precise && Permissions.held(fine) && on(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
          else if (on(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
          else LocationManager.PASSIVE_PROVIDER
        try start(pick)
        catch { case t: SecurityException => done(LocationAnswer.LocationDenied) }
      }
    }

  private def located(l: AndroidLocation): LocationAnswer = LocationAnswer.Located(Position(l.getLatitude, l.getLongitude,
    if (l.hasAccuracy) l.getAccuracy.toDouble else 0.0, if (l.hasAltitude) Some(l.getAltitude) else None,
    if (l.hasBearing) Some(l.getBearing.toDouble) else None, if (l.hasSpeed) Some(l.getSpeed.toDouble) else None, l.getTime))
}
