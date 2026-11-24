package everyui.render

import android.app.PendingIntent
import android.content.{Context, Intent}
import android.content.pm.PackageManager
import android.location.{LocationListener, LocationManager, LocationRequest, Location => AndroidLocation}
import android.os.{Build, CancellationSignal, Handler, Looper}
import everyui.{Permission, SettingsPage}
import everyui.activity.ServiceRuntime
import everyui.background.Work
import everyui.location.{BackgroundAsk, LocationAnswer, Position, VisitOptions, Visits}
import scala.jdk.CollectionConverters.*

/** Where the device is (`everyui.location.Location`) through the framework's LocationManager (not Google Play
  * services, which EveryUI does not bring): the fused provider where the device has one (Android 12 and later), else
  * satellites for a precise fix and the network's for a rough one. The manifest declares ACCESS_FINE_LOCATION and
  * ACCESS_COARSE_LOCATION, asked for together, so the person chooses precise or approximate, and
  * ACCESS_BACKGROUND_LOCATION for the location with the app closed (sbt-everyui writes them from `everyuiLocation`).
  * A watch passes its distance to the platform too, which then wakes the app less. Answers come on the thread that
  * draws.
  *
  * Nothing here needs the activity: with the app closed (a job, a receiver) the application's context asks, and a
  * permission is only read, never asked for. Visits (`everyui.location.Visits`) hear the passive provider's positions
  * (those the device fixes for any app) at `BackgroundReceiver` through a pending intent, which Android forgets as
  * the device restarts or the app is updated (`Visits.resume` puts it back), and fix one every 15 minutes in work. */
object LocationAccess {
  private val fine = "android.permission.ACCESS_FINE_LOCATION"
  private val coarse = "android.permission.ACCESS_COARSE_LOCATION"
  private val background = "android.permission.ACCESS_BACKGROUND_LOCATION"
  /** The passive provider's positions, as `BackgroundReceiver` hears them. */
  val passiveIntent = "everyui.location.PASSIVE"
  private lazy val main = new Handler(Looper.getMainLooper)

  /** The activity where there is one, else the application's. */
  private def context: Context = if (Host.activity != null) Host.activity else ServiceRuntime.context

  private def manager: LocationManager = context.getSystemService(classOf[LocationManager])

  def supported(): Boolean = context.getPackageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

  private def held: Boolean = Permissions.held(fine) || Permissions.held(coarse)

  /** The location with the app closed held: from Android 10 its own permission, before it the location itself. */
  private def backgroundHeld: Boolean = held && (Build.VERSION.SDK_INT < 29 || Permissions.held(background))

  def permission(done: Permission => Unit): Unit =
    done(if (!supported()) Permission.Refused else if (held) Permission.Granted else Permissions.state(coarse))

  /** Asked for where there is a window; with none, only whether it is held. */
  def permit(done: Boolean => Unit): Unit =
    if (!supported()) done(false) else Permissions.askAll(List(fine, coarse))(_ => done(held))

  def backgroundPermission(done: Permission => Unit): Unit =
    if (!supported()) done(Permission.Refused)
    else if (Build.VERSION.SDK_INT < 29) permission(done)
    else if (backgroundHeld) done(Permission.Granted)
    else if (!Permissions.declared(background)) done(Permission.Refused)
    else done(Permissions.state(background))

  def permitBackground(done: Boolean => Unit): Unit =
    if (!supported()) done(false)
    else BackgroundAsk.step(Build.VERSION.SDK_INT, Permissions.declared(background), held, backgroundHeld) match {
      case BackgroundAsk.BackgroundGiven => done(true)
      case BackgroundAsk.BackgroundUndeclared =>
        Platform.log("location: the manifest does not declare ACCESS_BACKGROUND_LOCATION " +
          "(everyuiLocation := Some(LocationUse(background = true)))")
        done(false)
      case BackgroundAsk.ForegroundFirst =>
        permit(granted => if (granted) permitBackground(done) else done(false))
      case BackgroundAsk.BackgroundPrompt => Permissions.ask(background)(_ => done(backgroundHeld))
      case BackgroundAsk.BackgroundSettings =>
        // Android takes the person to the app's location page, and answers as they come back; where it no longer does
        // (refused there twice) it answers at once with the app never left, and the app's details are opened instead
        var left = false
        val stop = Platform.onAway(() => left = true)
        Permissions.ask(background) { _ =>
          stop()
          if (backgroundHeld || left) done(backgroundHeld)
          else SettingsPages.open(SettingsPage.AppLocation, _ => done(backgroundHeld))
        }
    }

  /** The freshest position any enabled provider kept, with no new fix; none with no window where the location with
    * the app closed is not allowed. */
  def lastKnown(done: Option[Position] => Unit): Unit =
    if (!supported() || !held || (Host.activity == null && !backgroundHeld)) done(None)
    else {
      val kept =
        try {
          val m = manager
          m.getProviders(true).asScala.toList.flatMap(p => Option(m.getLastKnownLocation(p)))
        }
        catch { case _: SecurityException => Nil }
      done(kept.sortBy(_.getTime).lastOption.map(position))
    }

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
      val heard = (l: AndroidLocation) => once(if (l == null) LocationAnswer.LocationUnavailable else located(l))
      if (Build.VERSION.SDK_INT >= 31)
        manager.getCurrentLocation(provider(precise), quality(precise, 0L).setDurationMillis((timeout * 1000.0).toLong)
          .build(), cancel, context.getMainExecutor, (l: AndroidLocation) => heard(l))
      else if (Build.VERSION.SDK_INT >= 30)
        manager.getCurrentLocation(provider(precise), cancel, context.getMainExecutor, (l: AndroidLocation) => heard(l))
      else {
        // before Android 11, one update asked for, and no longer once the timeout cancels it
        val single = listening(heard, () => ())
        cancel.setOnCancelListener(() => manager.removeUpdates(single))
        manager.requestSingleUpdate(provider(precise), single, Looper.getMainLooper)
      }
    }

  def watch(precise: Boolean, distance: Double, answer: LocationAnswer => Unit): () => Unit = {
    var listener: Option[LocationListener] = None
    var stopped = false
    allowed(answer) { provider =>
      if (!stopped) {
        val heard = listening(l => answer(located(l)), () => answer(LocationAnswer.LocationUnavailable))
        listener = Some(heard)
        if (Build.VERSION.SDK_INT >= 31)
          manager.requestLocationUpdates(provider(precise), quality(precise, 1000L)
            .setMinUpdateDistanceMeters(distance.toFloat).build(), context.getMainExecutor, heard)
        else if (Build.VERSION.SDK_INT >= 30)
          manager.requestLocationUpdates(provider(precise), 1000L, distance.toFloat, context.getMainExecutor, heard)
        else manager.requestLocationUpdates(provider(precise), 1000L, distance.toFloat, heard, Looper.getMainLooper)
      }
    }
    () => {
      stopped = true
      listener.foreach(l => manager.removeUpdates(l))
      listener = None
    }
  }

  /** Visits where the app's `BackgroundApp` is there to hear them (its manifest names one). */
  def visitsSupported(): Boolean = supported() && BackgroundAccess.supported()

  /** The passive provider's positions to `BackgroundReceiver` (at most one a minute, however near the last: a stay is
    * made of fixes at one place), and a fix every 15 minutes, once the location with the app closed is held. */
  def visitsStart(options: VisitOptions): Boolean =
    if (!visitsSupported()) false
    else if (!backgroundHeld) {
      Platform.log("visits: not started: the location with the app closed is not allowed (Location.permitBackground)")
      false
    }
    else try {
      manager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60000L, 0f, passive(ServiceRuntime.context))
      Work.every(Visits.workKey, 15)
    }
    catch {
      case t: SecurityException =>
        Platform.logError("visits: not started", t)
        false
    }

  def visitsStop(): Unit = {
    try manager.removeUpdates(passive(ServiceRuntime.context))
    catch { case t: Throwable => Platform.logError("visits: the passive positions not stopped", t) }
    Work.cancel(Visits.workKey)
    Work.cancel(Visits.checkKey)
  }

  /** The visits' work run once more after `delay` seconds: a stay begun, looked at again once it would be long enough. */
  def visitsCheck(delay: Double): Unit = Work.once(Visits.checkKey, delay = delay)

  /** The pending intent the passive provider's positions come in: one, made again alike, so a second request replaces
    * the first and removing it stops both. Mutable, since the platform adds the position to it. */
  private def passive(c: Context): PendingIntent = PendingIntent.getBroadcast(c, 7300,
    new Intent(c, classOf[BackgroundReceiver]).setAction(passiveIntent),
    PendingIntent.FLAG_UPDATE_CURRENT | (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))

  /** The position a passive intent carries, if any. */
  def passiveFixes(intent: Intent): List[Position] =
    Option(intent.getParcelableExtra[AndroidLocation](LocationManager.KEY_LOCATION_CHANGED)).map(position).toList

  /** `start` with the provider to ask, once the person has allowed a location (asking where they have not and there is
    * a window), and the device has one turned on; otherwise `done` hears why not. With no window, a location not
    * allowed with the app closed is denied. */
  private def allowed(done: LocationAnswer => Unit)(start: (Boolean => String) => Unit): Unit =
    if (!supported()) done(LocationAnswer.LocationUnsupported)
    else permit { granted =>
      if (!granted) done(LocationAnswer.LocationDenied)
      else if (Host.activity == null && !backgroundHeld) {
        Platform.log("location: with the app closed, the location is not allowed (Location.permitBackground)")
        done(LocationAnswer.LocationDenied)
      }
      else if (!enabled) done(LocationAnswer.LocationUnavailable)
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

  /** A request as precise as asked (Android 12 and later): the fused provider otherwise takes every request as a
    * balanced one, which never turns the satellites on, so a precise fix asked of it was the network's. */
  private def quality(precise: Boolean, every: Long): LocationRequest.Builder = new LocationRequest.Builder(every)
    .setQuality(if (precise) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)

  /** Whether location is turned on: Android 9 says so; before it, whether a provider other than the passive one is. */
  private def enabled: Boolean =
    if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
    else manager.getProviders(true).asScala.exists(_ != LocationManager.PASSIVE_PROVIDER)

  /** A listener hearing each place, and its provider turned off. Its status and its provider turned on are heard too,
    * as nothing: Android 11 made them default methods, and before it a listener without them is an AbstractMethodError
    * the first time the provider says anything. */
  private def listening(heard: AndroidLocation => Unit, off: () => Unit): LocationListener = new LocationListener {
    override def onLocationChanged(l: AndroidLocation): Unit = heard(l)
    override def onProviderDisabled(name: String): Unit = off()
    override def onProviderEnabled(name: String): Unit = ()
    override def onStatusChanged(name: String, status: Int, extras: android.os.Bundle): Unit = ()
  }

  private def located(l: AndroidLocation): LocationAnswer = LocationAnswer.Located(position(l))

  private def position(l: AndroidLocation): Position = Position(l.getLatitude, l.getLongitude,
    if (l.hasAccuracy) l.getAccuracy.toDouble else 0.0, if (l.hasAltitude) Some(l.getAltitude) else None,
    if (l.hasBearing) Some(l.getBearing.toDouble) else None, if (l.hasSpeed) Some(l.getSpeed.toDouble) else None,
    l.getTime)
}
