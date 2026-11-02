package everyui.render

import everyui.Permission
import everyui.push.PushAnswer

/** Push notifications (`everyui.push.Push`). An Apple TV shows an app's notifications as a badge at most, never as
  * something to read or tap, so there are none here: `supported` is false, the permission is refused, and a
  * subscription answers at once as unsupported. */
object PushAccess {
  def supported(): Boolean = false

  def listen(): Unit = ()

  def permission(done: Permission => Unit): Unit = done(Permission.Refused)

  def permit(done: Boolean => Unit): Unit = done(false)

  def subscribe(key: String, done: PushAnswer => Unit): Unit = done(PushAnswer.PushUnsupported)

  def unsubscribe(done: () => Unit): Unit = done()

  def close(tag: String): Unit = ()
}
