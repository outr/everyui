package everyui.render

import everyui.Permission
import everyui.location.LocationAnswer

/** Where the device is (`everyui.location.Location`). An Apple TV gives an app no location of its own, so there is none here: `supported` is false, the permission is refused, and
  * a fix or a watch answers at once as unsupported. */
object LocationAccess {
  def supported(): Boolean = false

  def permission(done: Permission => Unit): Unit = done(Permission.Refused)

  def permit(done: Boolean => Unit): Unit = done(false)

  def current(precise: Boolean, timeout: Double, done: LocationAnswer => Unit): Unit =
    done(LocationAnswer.LocationUnsupported)

  def watch(precise: Boolean, distance: Double, answer: LocationAnswer => Unit): () => Unit = {
    answer(LocationAnswer.LocationUnsupported)
    () => ()
  }
}
