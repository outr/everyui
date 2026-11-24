package everyui.render

import everyui.Permission
import everyui.location.{LocationAnswer, Position, VisitOptions}

/** Where the device is (`everyui.location.Location`). Java has no location service of its own for a desktop app to ask, so there is none here: `supported` is false, the permission is refused (with the
  * app closed too), a fix or a watch answers at once as unsupported, none is known, and there are no visits. */
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

  def backgroundPermission(done: Permission => Unit): Unit = done(Permission.Refused)

  def permitBackground(done: Boolean => Unit): Unit = done(false)

  def lastKnown(done: Option[Position] => Unit): Unit = done(None)

  def visitsSupported(): Boolean = false

  def visitsStart(options: VisitOptions): Boolean = false

  def visitsStop(): Unit = ()

  def visitsCheck(delay: Double): Unit = ()
}
