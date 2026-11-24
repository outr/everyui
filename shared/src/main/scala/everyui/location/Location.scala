package everyui.location

import everyui.Permission
import everyui.async.Task
import everyui.render.LocationAccess

/** Where the device is: once (`current`), or followed as it moves (`watch`), after the person allows it. The web asks
  * `navigator.geolocation`, Android its LocationManager (the manifest declares ACCESS_FINE_LOCATION and
  * ACCESS_COARSE_LOCATION), an iPhone Core Location. The desktop, a Roku and an Apple TV have no location
  * (`supported` false). Everything is heard on the thread that draws.
  *
  * With the app closed (`BackgroundApp.onWork`, `onVisit`), `current` and `lastKnown` answer once the person has let
  * the app know where the device is all the time (`permitBackground`); nothing there needs a window.
  *
  * {{{
  *   Location.current().map {
  *     case LocationAnswer.Located(p) => map.centre(p.latitude, p.longitude)
  *     case other => Toast.show("No location: " + other)
  *   }.start()
  *   val watch = Location.watch(distance = 25.0) {
  *     case LocationAnswer.Located(p) => track += p
  *     case _ => ()
  *   }
  *   ...
  *   watch.stop()
  * }}} */
object Location {
  /** Whether this platform can say where the device is. */
  def supported: Boolean = LocationAccess.supported()

  /** Whether the person lets the app know where the device is. */
  def permission: Task[Permission] = Task.async[Permission](done => LocationAccess.permission(done))

  /** Ask for it (the platform's own prompt), and whether it was given: on Android, a precise or an approximate
    * location, as the person chooses. `current` and `watch` ask by themselves where it has not been given. */
  def permit(): Task[Boolean] = Task.async[Boolean](done => LocationAccess.permit(done))

  /** Whether the person lets the app know where the device is with the app closed too: Android's "Allow all the time"
    * (Android 10 and later; before it, the location itself), an iPhone's "Always". The web gives a page none, so it
    * answers as `permission` does; the desktop, a Roku and an Apple TV refuse. */
  def backgroundPermission: Task[Permission] =
    Task.async[Permission](done => LocationAccess.backgroundPermission(done))

  /** Ask for the location with the app closed, and whether it was given, from the window: the location itself first
    * where it has not been given. Android 10 asks in its prompt; Android 11 and later open the app's location page in
    * the settings, where the person chooses "Allow all the time", answered as they come back (the app's details where
    * that page no longer opens); an iPhone asks for "Always". The Android manifest must declare
    * ACCESS_BACKGROUND_LOCATION (`everyuiLocation := LocationUse(background = true)`), and Google Play wants the app
    * to say why first, in its own words (CONSUMING.md). The web answers as `permit`. */
  def permitBackground(): Task[Boolean] = Task.async[Boolean](done => LocationAccess.permitBackground(done))

  /** The last position the device fixed, for this app or another, at once and without a new fix (so it may be old:
    * see its `time`), or None where there is none or the location is not allowed. */
  def lastKnown(): Task[Option[Position]] = Task.async[Option[Position]](done => LocationAccess.lastKnown(done))

  /** Where the device is now: a fresh fix, as `precise` as the platform can (satellites; without, the network's
    * rougher and quicker one), or LocationUnavailable after `timeout` seconds without one. With no window it asks for
    * nothing: LocationDenied where the location with the app closed has not been given. */
  def current(precise: Boolean = true, timeout: Double = 30.0): Task[LocationAnswer] =
    Task.async[LocationAnswer](done => LocationAccess.current(precise, timeout, done))

  /** The device's position as it moves, each one at least `distance` metres from the last heard (0 for every one the
    * platform gives), until the watch is stopped. */
  def watch(distance: Double = 0.0, precise: Boolean = true)(heard: LocationAnswer => Unit): LocationWatch = {
    val watch = new LocationWatch(distance, heard)
    watch.begin(LocationAccess.watch(precise, distance, watch.answer))
    watch
  }
}
