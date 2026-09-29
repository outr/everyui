package everyui.location

import everyui.Permission
import everyui.async.Task
import everyui.render.LocationAccess

/** Where the device is: once (`current`), or followed as it moves (`watch`), after the person allows it. The web asks
  * `navigator.geolocation`, Android its LocationManager (the manifest declares ACCESS_FINE_LOCATION and
  * ACCESS_COARSE_LOCATION). The desktop, a Roku and an Apple TV have no location (`supported` false). Everything is
  * heard on the thread that draws.
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

  /** Where the device is now: a fresh fix, as `precise` as the platform can (satellites; without, the network's
    * rougher and quicker one), or LocationUnavailable after `timeout` seconds without one. */
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
